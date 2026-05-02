package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.domain.enums.EarnStatus;
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
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PointEarnCommandServiceUnitTest {

    private static final Long USER_ID = 1L;
    private static final Long EARN_ID = 100L;

    private final PointEarnRepository earnRepository = mock(PointEarnRepository.class);
    private final PointUserRepository userRepository = mock(PointUserRepository.class);
    private final PointPolicyService policy = mock(PointPolicyService.class);

    private final PointEarnCommandService service =
        new PointEarnCommandService(earnRepository, userRepository, policy);

    @BeforeEach
    void setupDefaultPolicy() {
        when(policy.earnMin()).thenReturn(1L);
        when(policy.earnMax()).thenReturn(100_000L);
        when(policy.expiryMinDays()).thenReturn(1);
        when(policy.expiryMaxDays()).thenReturn(1825);
        when(policy.expiryDefaultDays()).thenReturn(365);
        when(policy.balanceMaxPerUser()).thenReturn(1_000_000L);
    }

    @Nested
    @DisplayName("적립 성공")
    class EarnSuccess {

        @Test
        @DisplayName("유효한 입력으로 적립하면 ACTIVE 상태의 PointEarn 이 저장되고 결과가 반환된다")
        void valid_input_persists_active_earn_and_returns_result() {
            PointUser user = mock(PointUser.class);
            given(user.effectiveMaxBalance(anyLong())).willReturn(1_000_000L);
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(0L);
            given(earnRepository.save(any(PointEarn.class)))
                .willAnswer(inv -> inv.getArgument(0));

            EarnPointResult result = service.earn(
                new EarnPointCommand(USER_ID, 1000L, PointSource.SYSTEM, 30));

            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.amount()).isEqualTo(1000L);
            assertThat(result.source()).isEqualTo(PointSource.SYSTEM);

            ArgumentCaptor<PointEarn> captor = ArgumentCaptor.forClass(PointEarn.class);
            verify(earnRepository).save(captor.capture());
            PointEarn saved = captor.getValue();
            assertThat(saved.getInitialAmount()).isEqualTo(1000L);
            assertThat(saved.getRemainingAmount()).isEqualTo(1000L);
            assertThat(saved.getStatus()).isEqualTo(EarnStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("적립 실패")
    class EarnFailure {

        @Test
        @DisplayName("amount 가 정책 최솟값(1) 미만이면 InvalidEarnAmountException 을 던지고 영속화하지 않는다")
        void throws_when_amount_is_below_policy_minimum() {
            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 0L, PointSource.SYSTEM, null)))
                .isInstanceOf(InvalidEarnAmountException.class);

            verify(userRepository, never()).findByUserIdForUpdate(anyLong());
            verify(earnRepository, never()).save(any());
        }

        @Test
        @DisplayName("amount 가 정책 최댓값(100,000)을 초과하면 InvalidEarnAmountException 을 던진다")
        void throws_when_amount_is_above_policy_maximum() {
            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 100_001L, PointSource.SYSTEM, null)))
                .isInstanceOf(InvalidEarnAmountException.class);
        }

        @Test
        @DisplayName("amount 가 음수이면 InvalidEarnAmountException 을 던진다")
        void throws_when_amount_is_negative() {
            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, -1L, PointSource.SYSTEM, null)))
                .isInstanceOf(InvalidEarnAmountException.class);
        }

        @Test
        @DisplayName("expiryDays 가 정책 최솟값(1) 미만이면 InvalidExpiryDaysException 을 던진다")
        void throws_when_expiry_days_below_minimum() {
            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 1000L, PointSource.SYSTEM, 0)))
                .isInstanceOf(InvalidExpiryDaysException.class);
        }

        @Test
        @DisplayName("expiryDays 가 정책 최댓값(1825) 이상이면 InvalidExpiryDaysException 을 던진다")
        void throws_when_expiry_days_at_or_above_maximum() {
            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 1000L, PointSource.SYSTEM, 1825)))
                .isInstanceOf(InvalidExpiryDaysException.class);
        }

        @Test
        @DisplayName("expiryDays 가 음수이면 InvalidExpiryDaysException 을 던진다")
        void throws_when_expiry_days_is_negative() {
            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 1000L, PointSource.SYSTEM, -1)))
                .isInstanceOf(InvalidExpiryDaysException.class);
        }

        @Test
        @DisplayName("현재 잔액과 신규 적립의 합이 회원 한도를 초과하면 BalanceLimitExceededException 을 던지고 영속화하지 않는다")
        void throws_when_balance_plus_amount_exceeds_user_limit() {
            PointUser user = mock(PointUser.class);
            given(user.effectiveMaxBalance(anyLong())).willReturn(1000L);
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(600L);

            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 401L, PointSource.SYSTEM, null)))
                .isInstanceOf(BalanceLimitExceededException.class);

            verify(earnRepository, never()).save(any(PointEarn.class));
        }

        @Test
        @DisplayName("회원 한도가 0 으로 설정된 경우 어떤 양수 amount 로 적립을 시도해도 BalanceLimitExceededException 을 던진다")
        void throws_when_user_limit_is_zero() {
            PointUser user = mock(PointUser.class);
            given(user.effectiveMaxBalance(anyLong())).willReturn(0L);
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(user));
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(0L);

            assertThatThrownBy(() ->
                service.earn(new EarnPointCommand(USER_ID, 1L, PointSource.SYSTEM, null)))
                .isInstanceOf(BalanceLimitExceededException.class);
        }
    }

    @Nested
    @DisplayName("적립취소 성공")
    class CancelEarnSuccess {

        @Test
        @DisplayName("취소 가능한 적립에 대해 cancelEarn 을 호출하면 도메인의 cancel 메소드가 호출된다")
        void invokes_domain_cancel_when_earn_is_cancellable() {
            PointEarn earn = mock(PointEarn.class);
            given(earn.getUserId()).willReturn(USER_ID);
            given(earn.isCancellable()).willReturn(true);
            given(earnRepository.findById(EARN_ID)).willReturn(Optional.of(earn));
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));

            service.cancelEarn(new CancelEarnCommand(EARN_ID));

            verify(earn).cancel(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("적립취소 실패")
    class CancelEarnFailure {

        @Test
        @DisplayName("존재하지 않는 earnId 로 취소를 시도하면 EarnNotFoundException 을 던지고 회원 락도 시도하지 않는다")
        void throws_when_earn_id_does_not_exist() {
            given(earnRepository.findById(EARN_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelEarn(new CancelEarnCommand(EARN_ID)))
                .isInstanceOf(EarnNotFoundException.class);

            verify(userRepository, never()).findByUserIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("적립 row 는 있는데 회원 row 가 없는 정합성 깨짐 상태에서는 IllegalStateException 을 던지고 cancel 을 호출하지 않는다")
        void throws_when_user_row_is_missing_for_existing_earn() {
            PointEarn earn = mock(PointEarn.class);
            given(earn.getUserId()).willReturn(USER_ID);
            given(earnRepository.findById(EARN_ID)).willReturn(Optional.of(earn));
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelEarn(new CancelEarnCommand(EARN_ID)))
                .isInstanceOf(IllegalStateException.class);

            verify(earn, never()).cancel(any());
        }

        @Test
        @DisplayName("이미 취소되었거나 일부 사용된 적립(isCancellable=false)을 취소 시도하면 EarnCancelNotAllowedException 을 던지고 cancel 을 호출하지 않는다")
        void throws_when_earn_is_not_cancellable() {
            PointEarn earn = mock(PointEarn.class);
            given(earn.getUserId()).willReturn(USER_ID);
            given(earn.isCancellable()).willReturn(false);
            given(earn.getStatus()).willReturn(EarnStatus.CANCELLED);
            given(earn.getRemainingAmount()).willReturn(0L);
            given(earn.getInitialAmount()).willReturn(1000L);
            given(earnRepository.findById(EARN_ID)).willReturn(Optional.of(earn));
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));

            assertThatThrownBy(() -> service.cancelEarn(new CancelEarnCommand(EARN_ID)))
                .isInstanceOf(EarnCancelNotAllowedException.class);

            verify(earn, never()).cancel(any());
        }
    }
}
