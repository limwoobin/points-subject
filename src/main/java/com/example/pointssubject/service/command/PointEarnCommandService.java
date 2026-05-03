package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.domain.enums.EarnType;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.policy.PointPolicyService;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.CancelEarnCommand;
import com.example.pointssubject.service.command.dto.CancelEarnResult;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointEarnCommandService {

    private final PointEarnRepository earnRepository;
    private final PointUserRepository userRepository;
    private final PointPolicyService policy;
    private final PointActionLogger actionLogger;

    @Transactional
    public EarnPointResult earn(EarnPointCommand command) {
        return doEarn(command, EarnType.SYSTEM);
    }

    /** admin 전용 — 외부 노출 시 인증 게이트 필수. */
    @Transactional
    public EarnPointResult earnManual(EarnPointCommand command) {
        return doEarn(command, EarnType.MANUAL);
    }

    private EarnPointResult doEarn(EarnPointCommand command, EarnType type) {
        validateAmount(command.amount());

        int expiryDays = resolveExpiryDays(command.expiryDays());
        validateExpiryDays(expiryDays);

        LocalDateTime now = LocalDateTime.now();
        PointUser user = lockOrCreateUser(command.userId());

        long currentBalance = earnRepository.sumActiveBalance(command.userId(), now);
        long limit = user.effectiveMaxBalance(policy.balanceMaxPerUser());
        if (currentBalance + command.amount() > limit) {
            throw new PointException(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED,
                "currentBalance=" + currentBalance + ", requested=" + command.amount() + ", limit=" + limit);
        }

        PointEarn earn = PointEarn.earn(
            command.userId(),
            command.amount(),
            type,
            now.plusDays(expiryDays)
        );

        PointEarn saved = earnRepository.save(earn);
        actionLogger.earn(saved.getUserId(), saved.getId(), saved.getInitialAmount());
        return EarnPointResult.from(saved);
    }

    @Transactional
    public CancelEarnResult cancelEarn(CancelEarnCommand command) {
        Long earnId = command.earnId();
        Long userId = command.userId();

        userRepository.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new PointException(PointErrorCode.EARN_NOT_FOUND, "earnId=" + earnId));

        PointEarn earn = earnRepository.findById(earnId)
            .orElseThrow(() -> new PointException(PointErrorCode.EARN_NOT_FOUND, "earnId=" + earnId));

        if (!earn.getUserId().equals(userId)) {
            throw new PointException(PointErrorCode.EARN_NOT_FOUND, "earnId=" + earnId);
        }

        if (!earn.isCancellable()) {
            throw new PointException(PointErrorCode.EARN_CANCEL_NOT_ALLOWED,
                "earnId=" + earn.getId()
                    + ", status=" + earn.getStatus()
                    + ", remaining=" + earn.getRemainingAmount()
                    + ", initial=" + earn.getInitialAmount());
        }

        earn.cancel(LocalDateTime.now());
        actionLogger.earnCancel(earn.getUserId(), earn.getId(), earn.getInitialAmount());
        return CancelEarnResult.from(earn);
    }

    private void validateAmount(long amount) {
        long min = policy.earnMin();
        long max = policy.earnMax();
        if (amount < min || amount > max) {
            throw new PointException(PointErrorCode.EARN_AMOUNT_OUT_OF_RANGE,
                "amount=" + amount + ", allowed=" + min + "~" + max);
        }
    }

    private int resolveExpiryDays(Integer requested) {
        return requested != null ? requested : policy.expiryDefaultDays();
    }

    private void validateExpiryDays(int days) {
        int min = policy.expiryMinDays();
        int max = policy.expiryMaxDays();
        if (days < min || days >= max) {
            throw new PointException(PointErrorCode.EARN_EXPIRY_OUT_OF_RANGE,
                "days=" + days + ", allowed=" + min + " <= days < " + max);
        }
    }

    private PointUser lockOrCreateUser(Long userId) {
        return userRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> userRepository.save(PointUser.create(userId)));
    }
}
