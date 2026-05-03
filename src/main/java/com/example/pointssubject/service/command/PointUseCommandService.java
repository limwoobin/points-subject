package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.UseAllocation;
import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.entity.PointUseDetail;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.domain.enums.PointUseType;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.policy.PointPolicyService;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUseDetailRepository;
import com.example.pointssubject.repository.PointUseRepository;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.CancelUsePointCommand;
import com.example.pointssubject.service.command.dto.CancelUsePointResult;
import com.example.pointssubject.service.command.dto.UsePointCommand;
import com.example.pointssubject.service.command.dto.UsePointResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointUseCommandService {

    private final PointEarnRepository earnRepository;
    private final PointUseRepository useRepository;
    private final PointUseDetailRepository useDetailRepository;
    private final PointUserRepository userRepository;
    private final PointPolicyService policy;
    private final PointActionLogger actionLogger;

    @Transactional
    public UsePointResult use(UsePointCommand command) {
        lockOrCreateUser(command.userId());

        if (useRepository.existsByOrderNumberAndType(command.orderNumber(), PointUseType.USE)) {
            throw new PointException(PointErrorCode.USE_ORDER_NUMBER_DUPLICATED, "orderNumber=" + command.orderNumber());
        }

        LocalDateTime now = LocalDateTime.now();
        long available = earnRepository.sumActiveBalance(command.userId(), now);
        if (available < command.amount()) {
            throw new PointException(PointErrorCode.USE_INSUFFICIENT_BALANCE, "available=" + available + ", requested=" + command.amount());
        }

        List<PointEarn> candidates = earnRepository.findActiveCandidatesForUse(command.userId(), now)
            .stream()
            .sorted(PointEarn.USE_PRIORITY)
            .toList();
        List<UseAllocation> allocations = allocate(candidates, command.amount());

        PointUse savedUse = useRepository.save(PointUse.use(command.userId(), command.orderNumber(), command.amount()));
        for (UseAllocation a : allocations) {
            useDetailRepository.save(PointUseDetail.of(savedUse.getId(), a.earnId(), a.amount()));
        }

        actionLogger.use(savedUse.getUserId(), savedUse.getId(), savedUse.getAmount(), savedUse.getOrderNumber());
        return UsePointResult.from(savedUse, allocations);
    }

    /**
     * detail FIFO 분배: 살아있으면 restore, 만료/취소면 reissue.
     * orderRefundId 재요청은 idempotent.
     * */
    @Transactional
    public CancelUsePointResult cancelUse(CancelUsePointCommand command) {
        lockOrCreateUser(command.userId());

        if (useRepository.findByOrderRefundId(command.orderRefundId()).isPresent()) {
            throw new PointException(PointErrorCode.ORDER_REFUND_ID_DUPLICATED, "orderRefundId=" + command.orderRefundId());
        }

        PointUse originalUse = useRepository.findByOrderNumberAndType(command.orderNumber(), PointUseType.USE)
            .orElseThrow(() -> new PointException(PointErrorCode.USE_NOT_FOUND, "orderNumber=" + command.orderNumber()));

        if (!originalUse.getUserId().equals(command.userId())) {
            throw new PointException(PointErrorCode.USE_NOT_FOUND, "orderNumber=" + command.orderNumber() + ", userId=" + command.userId());
        }

        long alreadyCancelled = useRepository.sumCancelledByUseId(originalUse.getId());
        long cancellable = originalUse.getAmount() - alreadyCancelled;
        if (command.amount() > cancellable) {
            throw new PointException(PointErrorCode.USE_CANCEL_AMOUNT_EXCEEDED,
                "orderNumber=" + originalUse.getOrderNumber()
                    + ", cancellable=" + cancellable
                    + ", requested=" + command.amount());
        }

        PointUse cancelRow = useRepository.save(PointUse.useCancel(
            originalUse.getUserId(),
            originalUse.getOrderNumber(),
            originalUse.getId(),
            command.orderRefundId(),
            command.amount()
        ));

        distributeRefund(originalUse, cancelRow, command.amount());
        actionLogger.useCancel(
            cancelRow.getUserId(),
            cancelRow.getId(),
            cancelRow.getAmount(),
            cancelRow.getOrderNumber(),
            cancelRow.getOrderRefundId()
        );

        long remainingCancellable = originalUse.getAmount() - (alreadyCancelled + command.amount());
        return CancelUsePointResult.from(cancelRow, remainingCancellable);
    }

    private List<UseAllocation> allocate(List<PointEarn> candidates, long requested) {
        List<UseAllocation> allocations = new ArrayList<>();
        long remaining = requested;

        for (PointEarn earn : candidates) {
            if (remaining <= 0) {
                break;
            }

            long take = Math.min(earn.getRemainingAmount(), remaining);
            earn.useFrom(take);
            allocations.add(new UseAllocation(earn.getId(), take));
            remaining -= take;
        }

        if (remaining > 0) {
            throw new IllegalStateException("Allocation underflow: shortage=" + remaining);
        }

        return allocations;
    }

    private void distributeRefund(PointUse originalUse, PointUse cancelRow, long requestedAmount) {
        LocalDateTime now = LocalDateTime.now();
        List<PointUseDetail> details = useDetailRepository.findByUseIdOrderByIdAsc(originalUse.getId());
        Map<Long, Long> alreadyRefunded = sumPreviousRefundsByEarnId(originalUse.getId());
        Map<Long, PointEarn> origins = loadOriginEarns(details);

        long remaining = requestedAmount;
        for (PointUseDetail detail : details) {
            if (remaining <= 0) break;
            long take = Math.min(remaining, detail.getAmount() - alreadyRefunded.getOrDefault(detail.getEarnId(), 0L));
            if (take <= 0) continue;

            PointEarn pointEarn = requireOrigin(origins, detail.getEarnId());
            Long landedEarnId = land(pointEarn, take, cancelRow, now);
            useDetailRepository.save(PointUseDetail.of(cancelRow.getId(), landedEarnId, take));
            remaining -= take;
        }

        if (remaining > 0) {
            throw new IllegalStateException("Cancel allocation underflow: useId=" + originalUse.getId() + ", shortage=" + remaining);
        }
    }

    /** alive 면 origin 적립에 복원, 만료면 새 적립을 발급해 거기로 환불금을 적재. landed earn id 반환. */
    private Long land(PointEarn origin, long amount, PointUse cancelRow, LocalDateTime now) {
        if (origin.isAlive(now)) {
            origin.restoreFromUseCancel(amount);
            return origin.getId();
        }

        PointEarn reissued = earnRepository.save(PointEarn.reissueFromUseCancel(
            origin.getUserId(),
            amount,
            cancelRow.getId(),
            now.plusDays(policy.useCancelReissueDays()))
        );

        return reissued.getId();
    }

    private Map<Long, PointEarn> loadOriginEarns(List<PointUseDetail> details) {
        List<Long> earnIds = details.stream()
                .map(PointUseDetail::getEarnId)
                .distinct()
                .toList();

        return earnRepository.findAllById(earnIds).stream()
            .collect(Collectors.toMap(PointEarn::getId, Function.identity()));
    }

    private PointEarn requireOrigin(Map<Long, PointEarn> origins, Long earnId) {
        PointEarn origin = origins.get(earnId);
        if (origin == null) {
            throw new IllegalStateException("origin earn missing: id=" + earnId);
        }

        return origin;
    }

    private Map<Long, Long> sumPreviousRefundsByEarnId(Long originalUseId) {
        Map<Long, Long> map = new HashMap<>();
        for (PointUseDetail d : useDetailRepository.findCancelDetailsByOriginalUseId(originalUseId)) {
            map.merge(d.getEarnId(), d.getAmount(), Long::sum);
        }

        return map;
    }

    private void lockOrCreateUser(Long userId) {
        userRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> userRepository.save(PointUser.create(userId)));
    }

}
