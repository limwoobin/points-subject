package com.example.pointssubject.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.EarnType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** PointEarn 도메인 invariant — useFrom / restoreFromUseCancel / cancel 의 경계와 거부 케이스. */
class PointEarnInvariantTest {

    private static final Long USER_ID = 1L;

    private PointEarn newActive(long initial) {
        return PointEarn.earn(USER_ID, initial, EarnType.SYSTEM, LocalDateTime.now().plusDays(30));
    }

    @Nested
    @DisplayName("팩토리 — earn")
    class EarnFactory {

        @Test
        @DisplayName("PointEarn.earn 은 type=NORMAL, status=ACTIVE, remaining=initial 으로 시작한다")
        void earn_starts_active_normal() {
            PointEarn earn = newActive(1000L);
            assertThat(earn.getType()).isEqualTo(EarnType.SYSTEM);
            assertThat(earn.getStatus()).isEqualTo(EarnStatus.ACTIVE);
            assertThat(earn.getInitialAmount()).isEqualTo(1000L);
            assertThat(earn.getRemainingAmount()).isEqualTo(1000L);
            assertThat(earn.getOriginUseCancelId()).isNull();
            assertThat(earn.getCancelledAt()).isNull();
        }

        @Test
        @DisplayName("PointEarn.reissueFromUseCancel 은 type=REISSUE 로 발급되고 originUseCancelId 가 채워진다")
        void reissue_factory_sets_type_and_link() {
            PointEarn reissued = PointEarn.reissueFromUseCancel(
                USER_ID, 500L, 99L, LocalDateTime.now().plusDays(365));
            assertThat(reissued.getType()).isEqualTo(EarnType.USE_CANCEL_REISSUE);
            assertThat(reissued.getOriginUseCancelId()).isEqualTo(99L);
            assertThat(reissued.getInitialAmount()).isEqualTo(500L);
            assertThat(reissued.getRemainingAmount()).isEqualTo(500L);
            assertThat(reissued.getStatus()).isEqualTo(EarnStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("isCancellable / isAlive")
    class StatePredicates {

        @Test
        @DisplayName("미사용 ACTIVE 적립은 isCancellable=true 이고, 일부 사용된 적립은 false")
        void cancellable_only_when_remaining_equals_initial() {
            PointEarn earn = newActive(1000L);
            assertThat(earn.isCancellable()).isTrue();

            earn.useFrom(1L);
            assertThat(earn.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("CANCELLED 상태의 적립은 isCancellable=false")
        void cancelled_earn_is_not_cancellable() {
            PointEarn earn = newActive(1000L);
            earn.cancel(LocalDateTime.now());
            assertThat(earn.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("isAlive 는 ACTIVE 이고 expiresAt > now 일 때만 true")
        void alive_requires_active_and_unexpired() {
            PointEarn alive = PointEarn.earn(USER_ID, 100L, EarnType.SYSTEM, LocalDateTime.now().plusDays(1));
            assertThat(alive.isAlive(LocalDateTime.now())).isTrue();

            PointEarn expired = PointEarn.earn(USER_ID, 100L, EarnType.SYSTEM, LocalDateTime.now().minusDays(1));
            assertThat(expired.isAlive(LocalDateTime.now())).isFalse();

            PointEarn cancelled = newActive(100L);
            cancelled.cancel(LocalDateTime.now());
            assertThat(cancelled.isAlive(LocalDateTime.now())).isFalse();
        }
    }

    @Nested
    @DisplayName("useFrom — 차감 invariant")
    class UseFrom {

        @Test
        @DisplayName("정상 차감 시 remainingAmount 가 그만큼 줄어든다")
        void deducts_remaining() {
            PointEarn earn = newActive(1000L);
            earn.useFrom(300L);
            assertThat(earn.getRemainingAmount()).isEqualTo(700L);
        }

        @Test
        @DisplayName("amount=0 또는 음수면 IllegalStateException 을 던진다")
        void rejects_non_positive_amount() {
            PointEarn earn = newActive(1000L);
            assertThatIllegalStateException().isThrownBy(() -> earn.useFrom(0L));
            assertThatIllegalStateException().isThrownBy(() -> earn.useFrom(-1L));
        }

        @Test
        @DisplayName("amount 가 remaining 을 초과하면 IllegalStateException 을 던진다 (음수 잔액 차단)")
        void rejects_over_remaining() {
            PointEarn earn = newActive(100L);
            assertThatIllegalStateException().isThrownBy(() -> earn.useFrom(101L));
            assertThat(earn.getRemainingAmount()).isEqualTo(100L);
        }

        @Test
        @DisplayName("CANCELLED 상태의 적립에서 차감을 시도하면 IllegalStateException 을 던진다")
        void rejects_when_cancelled() {
            PointEarn earn = newActive(1000L);
            earn.cancel(LocalDateTime.now());
            assertThatIllegalStateException().isThrownBy(() -> earn.useFrom(1L));
        }
    }

    @Nested
    @DisplayName("restoreFromUseCancel — 복원 invariant")
    class RestoreFromUseCancel {

        @Test
        @DisplayName("일부 사용된 적립을 복원하면 remainingAmount 가 그만큼 증가한다")
        void increases_remaining() {
            PointEarn earn = newActive(1000L);
            earn.useFrom(600L);
            earn.restoreFromUseCancel(200L);
            assertThat(earn.getRemainingAmount()).isEqualTo(600L);
        }

        @Test
        @DisplayName("amount=0 또는 음수면 IllegalStateException 을 던진다")
        void rejects_non_positive_amount() {
            PointEarn earn = newActive(1000L);
            earn.useFrom(500L);
            assertThatIllegalStateException().isThrownBy(() -> earn.restoreFromUseCancel(0L));
            assertThatIllegalStateException().isThrownBy(() -> earn.restoreFromUseCancel(-1L));
        }

        @Test
        @DisplayName("복원 후 remainingAmount 가 initialAmount 를 초과하면 IllegalStateException 을 던진다 (복원 누적 오버플로우 차단)")
        void rejects_overflow_above_initial() {
            PointEarn earn = newActive(1000L);
            earn.useFrom(500L);
            // 600 복원 시 remaining 1100 > initial 1000 → 거부
            assertThatIllegalStateException().isThrownBy(() -> earn.restoreFromUseCancel(600L));
            assertThat(earn.getRemainingAmount()).isEqualTo(500L);
        }

        @Test
        @DisplayName("CANCELLED 상태의 적립을 복원하려 하면 IllegalStateException 을 던진다")
        void rejects_when_cancelled() {
            PointEarn earn = newActive(1000L);
            earn.cancel(LocalDateTime.now());
            assertThatIllegalStateException().isThrownBy(() -> earn.restoreFromUseCancel(1L));
        }
    }

    @Nested
    @DisplayName("cancel — 적립취소 invariant")
    class Cancel {

        @Test
        @DisplayName("미사용 ACTIVE 적립을 취소하면 status=CANCELLED, remaining=0, cancelledAt 채워진다")
        void cancels_unused_active() {
            PointEarn earn = newActive(1000L);
            LocalDateTime now = LocalDateTime.now();
            earn.cancel(now);
            assertThat(earn.getStatus()).isEqualTo(EarnStatus.CANCELLED);
            assertThat(earn.getRemainingAmount()).isZero();
            assertThat(earn.getCancelledAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("일부 사용된 적립은 취소 불가 — IllegalStateException")
        void rejects_when_partially_used() {
            PointEarn earn = newActive(1000L);
            earn.useFrom(1L);
            assertThatIllegalStateException().isThrownBy(() -> earn.cancel(LocalDateTime.now()));
        }

        @Test
        @DisplayName("이미 취소된 적립을 다시 취소하면 IllegalStateException")
        void rejects_when_already_cancelled() {
            PointEarn earn = newActive(1000L);
            earn.cancel(LocalDateTime.now());
            assertThatIllegalStateException().isThrownBy(() -> earn.cancel(LocalDateTime.now()));
        }
    }
}
