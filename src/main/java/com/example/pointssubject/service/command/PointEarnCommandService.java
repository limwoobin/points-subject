package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.domain.enums.PointSource;
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

/**
 * 적립/적립취소가 동일 aggregate({@code point_earn}) + 회원 락을 공유하므로 한 서비스에 묶음.
 * 동시성: 회원 row 비관 락으로 적립/사용/사용취소를 직렬화하고, 적립 row 의 {@code @Version}
 * 으로 락 획득 직전의 변경을 commit 시점에 잡아 롤백.
 * <p>
 * {@code source} 는 외부 입력이 아니라 진입점으로 결정된다 — {@link #earn(EarnPointCommand)} 는 SYSTEM,
 * {@link #earnManual(EarnPointCommand)} 는 MANUAL. 일반 사용자가 자기 적립을 수기 적립으로 둔갑시키는 시나리오를 차단.
 */
@Service
@RequiredArgsConstructor
public class PointEarnCommandService {

    private final PointEarnRepository earnRepository;
    private final PointUserRepository userRepository;
    private final PointPolicyService policy;

    /** 일반(시스템) 적립. 클라이언트 호출 경로. */
    @Transactional
    public EarnPointResult earn(EarnPointCommand command) {
        return doEarn(command, PointSource.SYSTEM);
    }

    /** 운영자 수기 적립. admin 엔드포인트 전용 — 외부 노출 시 인증 게이트 필수. */
    @Transactional
    public EarnPointResult earnManual(EarnPointCommand command) {
        return doEarn(command, PointSource.MANUAL);
    }

    private EarnPointResult doEarn(EarnPointCommand command, PointSource source) {
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
            source,
            now.plusDays(expiryDays)
        );
        PointEarn saved = earnRepository.save(earn);
        return EarnPointResult.from(saved);
    }

    @Transactional
    public CancelEarnResult cancelEarn(CancelEarnCommand command) {
        Long earnId = command.earnId();

        PointEarn earn = earnRepository.findById(earnId)
            .orElseThrow(() -> new PointException(PointErrorCode.EARN_NOT_FOUND, "earnId=" + earnId));

        userRepository.findByUserIdForUpdate(earn.getUserId())
            .orElseThrow(() -> new IllegalStateException(
                "user row missing for earnId=" + earnId + ", userId=" + earn.getUserId())
            );

        if (!earn.isCancellable()) {
            throw new PointException(PointErrorCode.EARN_CANCEL_NOT_ALLOWED,
                "earnId=" + earn.getId()
                    + ", status=" + earn.getStatus()
                    + ", remaining=" + earn.getRemainingAmount()
                    + ", initial=" + earn.getInitialAmount());
        }

        earn.cancel(LocalDateTime.now());
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
