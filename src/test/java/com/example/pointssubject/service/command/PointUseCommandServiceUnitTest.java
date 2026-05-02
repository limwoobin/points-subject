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

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.entity.PointUseDetail;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUseDetailRepository;
import com.example.pointssubject.repository.PointUseRepository;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.UsePointCommand;
import com.example.pointssubject.service.command.dto.UsePointResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PointUseCommandServiceUnitTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_NUMBER = "ORD-A1234";

    private final PointEarnRepository earnRepository = mock(PointEarnRepository.class);
    private final PointUseRepository useRepository = mock(PointUseRepository.class);
    private final PointUseDetailRepository useDetailRepository = mock(PointUseDetailRepository.class);
    private final PointUserRepository userRepository = mock(PointUserRepository.class);

    private final PointUseCommandService service = new PointUseCommandService(
        earnRepository, useRepository, useDetailRepository, userRepository);

    @Nested
    @DisplayName("사용 성공")
    class UseSuccess {

        @Test
        @DisplayName("사용 가능 잔액이 충분한 단일 적립을 사용 요청하면 PointUse 와 PointUseDetail 이 저장되고 결과가 반환된다")
        void uses_from_single_earn_and_persists() {
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.empty());
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(1000L);

            PointEarn earn = mock(PointEarn.class);
            given(earn.getId()).willReturn(10L);
            given(earn.getRemainingAmount()).willReturn(1000L);
            given(earnRepository.findActiveCandidatesForUse(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(List.of(earn));

            given(useRepository.save(any(PointUse.class))).willAnswer(inv -> inv.getArgument(0));

            UsePointResult result = service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 600L));

            assertThat(result.amount()).isEqualTo(600L);
            assertThat(result.allocations()).hasSize(1);
            assertThat(result.allocations().get(0).earnId()).isEqualTo(10L);
            assertThat(result.allocations().get(0).amount()).isEqualTo(600L);
            verify(earn).useFrom(600L);
            verify(useDetailRepository).save(any(PointUseDetail.class));
        }

        @Test
        @DisplayName("여러 적립에 걸쳐 사용 시 우선순위(첫번째 후보) 적립부터 차례로 차감되고 detail row 가 차감 적립 수만큼 생성된다")
        void splits_across_multiple_earns_in_order() {
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.empty());
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(1500L);

            PointEarn first = mock(PointEarn.class);
            given(first.getId()).willReturn(10L);
            given(first.getRemainingAmount()).willReturn(1000L);
            PointEarn second = mock(PointEarn.class);
            given(second.getId()).willReturn(11L);
            given(second.getRemainingAmount()).willReturn(500L);
            given(earnRepository.findActiveCandidatesForUse(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(List.of(first, second));

            given(useRepository.save(any(PointUse.class))).willAnswer(inv -> inv.getArgument(0));

            UsePointResult result = service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 1200L));

            assertThat(result.allocations()).hasSize(2);
            assertThat(result.allocations().get(0).amount()).isEqualTo(1000L);
            assertThat(result.allocations().get(1).amount()).isEqualTo(200L);
            verify(first).useFrom(1000L);
            verify(second).useFrom(200L);
        }
    }

    @Nested
    @DisplayName("사용 실패")
    class UseFailure {

        @Test
        @DisplayName("amount 가 0 이면 USE_AMOUNT_INVALID 를 던지고 락도 시도하지 않는다")
        void throws_when_amount_is_zero() {
            assertThatThrownBy(() -> service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 0L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_AMOUNT_INVALID);

            verify(userRepository, never()).findByUserIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("amount 가 음수이면 USE_AMOUNT_INVALID 를 던진다")
        void throws_when_amount_is_negative() {
            assertThatThrownBy(() -> service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, -1L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_AMOUNT_INVALID);
        }

        @Test
        @DisplayName("회원 row 자체가 없으면 사용 가능 잔액이 없다고 보아 USE_INSUFFICIENT_BALANCE 를 던진다")
        void throws_when_user_row_missing() {
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 100L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("같은 orderNumber 의 PointUse 가 이미 존재하면 USE_ORDER_NUMBER_DUPLICATED 를 던지고 적립을 차감하지 않는다")
        void throws_when_order_number_already_used() {
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.findByOrderNumber(ORDER_NUMBER))
                .willReturn(Optional.of(mock(PointUse.class)));

            assertThatThrownBy(() -> service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 100L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_ORDER_NUMBER_DUPLICATED);

            verify(earnRepository, never()).findActiveCandidatesForUse(anyLong(), any());
            verify(useRepository, never()).save(any(PointUse.class));
        }

        @Test
        @DisplayName("회원의 사용 가능 잔액 합계가 요청 amount 보다 작으면 USE_INSUFFICIENT_BALANCE 를 던지고 차감/저장하지 않는다")
        void throws_when_balance_is_insufficient() {
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.empty());
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(500L);

            assertThatThrownBy(() -> service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 1000L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_INSUFFICIENT_BALANCE);

            verify(earnRepository, never()).findActiveCandidatesForUse(anyLong(), any());
            verify(useRepository, never()).save(any(PointUse.class));
        }
    }

    @Nested
    @DisplayName("PointUse 저장 인자 검증")
    class PersistedShape {

        @Test
        @DisplayName("저장되는 PointUse 는 요청의 userId/orderNumber/amount 를 그대로 가지고 cancelledAmount 는 0 이다")
        void persisted_use_carries_request_payload() {
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.empty());
            given(earnRepository.sumActiveBalance(eq(USER_ID), any(LocalDateTime.class))).willReturn(1000L);

            PointEarn earn = mock(PointEarn.class);
            given(earn.getId()).willReturn(10L);
            given(earn.getRemainingAmount()).willReturn(1000L);
            given(earnRepository.findActiveCandidatesForUse(eq(USER_ID), any(LocalDateTime.class)))
                .willReturn(List.of(earn));
            given(useRepository.save(any(PointUse.class))).willAnswer(inv -> inv.getArgument(0));

            service.use(new UsePointCommand(USER_ID, ORDER_NUMBER, 700L));

            ArgumentCaptor<PointUse> captor = ArgumentCaptor.forClass(PointUse.class);
            verify(useRepository).save(captor.capture());
            PointUse saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getOrderNumber()).isEqualTo(ORDER_NUMBER);
            assertThat(saved.getAmount()).isEqualTo(700L);
            assertThat(saved.getCancelledAmount()).isZero();
        }
    }
}
