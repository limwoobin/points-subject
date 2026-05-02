package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.domain.enums.PointSource;
import com.example.pointssubject.exception.BalanceLimitExceededException;
import com.example.pointssubject.exception.EarnCancelNotAllowedException;
import com.example.pointssubject.exception.EarnNotFoundException;
import com.example.pointssubject.exception.InvalidEarnAmountException;
import com.example.pointssubject.exception.InvalidExpiryDaysException;
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

/**
 * 적립/적립취소가 동일 aggregate({@code point_earn}) + 회원 락을 공유하므로 한 서비스에 묶음.
 * 동시성: 회원 row 비관 락으로 적립/사용/사용취소를 직렬화하고, 적립 row 의 {@code @Version}
 * 으로 락 획득 직전의 변경을 commit 시점에 잡아 롤백.
 */
@Service
@RequiredArgsConstructor
public class PointEarnCommandService {

    private final PointEarnRepository earnRepository;
    private final PointUserRepository userRepository;
    private final PointPolicyService policy;

    @Transactional
    public EarnPointResult earn(EarnPointCommand command) {
        validateAmount(command.amount());

        int expiryDays = resolveExpiryDays(command.expiryDays());
        validateExpiryDays(expiryDays);

        LocalDateTime now = LocalDateTime.now();
        PointUser user = lockOrCreateUser(command.userId());

        long currentBalance = earnRepository.sumActiveBalance(command.userId(), now);
        long limit = user.effectiveMaxBalance(policy.balanceMaxPerUser());
        if (currentBalance + command.amount() > limit) {
            throw new BalanceLimitExceededException(currentBalance, command.amount(), limit);
        }

        PointEarn earn = PointEarn.earn(
            command.userId(),
            command.amount(),
            command.source() != null ? command.source() : PointSource.SYSTEM,
            now.plusDays(expiryDays)
        );
        PointEarn saved = earnRepository.save(earn);
        return EarnPointResult.from(saved);
    }

    @Transactional
    public CancelEarnResult cancelEarn(CancelEarnCommand command) {
        Long earnId = command.earnId();

        PointEarn earn = earnRepository.findById(earnId)
            .orElseThrow(() -> new EarnNotFoundException(earnId));

        userRepository.findByUserIdForUpdate(earn.getUserId())
            .orElseThrow(() -> new IllegalStateException(
                "user row missing for earnId=" + earnId + ", userId=" + earn.getUserId())
            );

        if (!earn.isCancellable()) {
            throw new EarnCancelNotAllowedException(
                earn.getId(), earn.getStatus(), earn.getRemainingAmount(), earn.getInitialAmount());
        }

        earn.cancel(LocalDateTime.now());
        return CancelEarnResult.from(earn);
    }

    private void validateAmount(long amount) {
        long min = policy.earnMin();
        long max = policy.earnMax();
        if (amount < min || amount > max) {
            throw new InvalidEarnAmountException(amount, min, max);
        }
    }

    private int resolveExpiryDays(Integer requested) {
        return requested != null ? requested : policy.expiryDefaultDays();
    }

    private void validateExpiryDays(int days) {
        int min = policy.expiryMinDays();
        int max = policy.expiryMaxDays();
        if (days < min || days >= max) {
            throw new InvalidExpiryDaysException(days, min, max);
        }
    }

    private PointUser lockOrCreateUser(Long userId) {
        return userRepository.findByUserIdForUpdate(userId)
            .orElseGet(() -> userRepository.save(PointUser.create(userId)));
    }
}
