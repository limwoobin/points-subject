package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.entity.PointUseDetail;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUseDetailRepository;
import com.example.pointssubject.repository.PointUseRepository;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.UseAllocation;
import com.example.pointssubject.service.command.dto.UsePointCommand;
import com.example.pointssubject.service.command.dto.UsePointResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 사용 유스케이스. 동시성: 회원 row 비관 락으로 적립/사용/사용취소를 직렬화.
 * <p>
 * 분배 우선순위 (PRD §3.3.3): 수기(MANUAL) → 만료 임박 → 적립일 빠른 순.
 * 멱등성: order_number 중복은 application 레벨에서 차단 (DB UK 미적용).
 */
@Service
@RequiredArgsConstructor
public class PointUseCommandService {

    private final PointEarnRepository earnRepository;
    private final PointUseRepository useRepository;
    private final PointUseDetailRepository useDetailRepository;
    private final PointUserRepository userRepository;

    @Transactional
    public UsePointResult use(UsePointCommand command) {
        validateAmount(command.amount());

        userRepository.findByUserIdForUpdate(command.userId())
            .orElseThrow(() -> new PointException(PointErrorCode.USE_INSUFFICIENT_BALANCE,
                "userId=" + command.userId() + " (no balance)"));

        if (useRepository.findByOrderNumber(command.orderNumber()).isPresent()) {
            throw new PointException(PointErrorCode.USE_ORDER_NUMBER_DUPLICATED,
                "orderNumber=" + command.orderNumber());
        }

        LocalDateTime now = LocalDateTime.now();
        long available = earnRepository.sumActiveBalance(command.userId(), now);
        if (available < command.amount()) {
            throw new PointException(PointErrorCode.USE_INSUFFICIENT_BALANCE,
                "available=" + available + ", requested=" + command.amount());
        }

        List<PointEarn> candidates = earnRepository.findActiveCandidatesForUse(command.userId(), now);
        List<UseAllocation> allocations = allocate(candidates, command.amount());

        PointUse savedUse = useRepository.save(
            PointUse.use(command.userId(), command.orderNumber(), command.amount()));
        for (UseAllocation a : allocations) {
            useDetailRepository.save(PointUseDetail.of(savedUse.getId(), a.earnId(), a.amount()));
        }

        return UsePointResult.from(savedUse, allocations);
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
            // sumActiveBalance 통과했는데 후보 walk 중 부족이 발생하면 데이터 정합성 깨짐
            throw new IllegalStateException("Allocation underflow: shortage=" + remaining);
        }
        return allocations;
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new PointException(PointErrorCode.USE_AMOUNT_INVALID, "amount=" + amount);
        }
    }
}
