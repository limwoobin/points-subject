package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CancelUsePointCommandServiceUnitTest {

    private static final Long USER_ID = 1L;
    private static final String ORDER_NUMBER = "ORD-X";
    private static final String ORDER_REFUND_ID = "ORF-001";

    private final PointEarnRepository earnRepository = mock(PointEarnRepository.class);
    private final PointUseRepository useRepository = mock(PointUseRepository.class);
    private final PointUseDetailRepository useDetailRepository = mock(PointUseDetailRepository.class);
    private final PointUserRepository userRepository = mock(PointUserRepository.class);
    private final PointPolicyService policy = mock(PointPolicyService.class);
    private final PointActionLogger actionLogger = mock(PointActionLogger.class);

    private final PointUseCommandService service = new PointUseCommandService(
        earnRepository, useRepository, useDetailRepository, userRepository, policy, actionLogger);

    @Nested
    @DisplayName("멱등성 분기 (reject)")
    class Idempotency {

        @Test
        @DisplayName("orderRefundId 가 이미 우리 테이블에 존재하면 페이로드 동일 여부 무관하게 ORDER_REFUND_ID_DUPLICATED 를 던지고 DB 는 건드리지 않는다")
        void existing_refund_id_throws_duplicated() {
            PointUse existingCancel = PointUse.useCancel(USER_ID, ORDER_NUMBER, 50L, ORDER_REFUND_ID, 600L);
            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.of(existingCancel));

            assertThatThrownBy(() -> service.cancelUse(
                    new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 600L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.ORDER_REFUND_ID_DUPLICATED);

            verify(useRepository, never()).save(any(PointUse.class));
            verify(earnRepository, never()).save(any(PointEarn.class));
        }
    }

    @Nested
    @DisplayName("invariant 검증")
    class Invariants {

        @Test
        @DisplayName("orderNumber 의 USE row 가 존재하지 않으면 USE_NOT_FOUND 가 발생한다")
        void missing_use_throws_not_found() {
            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.empty());
            given(useRepository.findByOrderNumberAndType(ORDER_NUMBER, PointUseType.USE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelUse(
                    new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 100L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 회원의 use 를 취소하려 하면 존재 자체를 노출하지 않도록 USE_NOT_FOUND 가 발생한다")
        void other_users_use_throws_not_found() {
            PointUse otherUse = PointUse.use(99L, ORDER_NUMBER, 1000L);
            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.empty());
            given(useRepository.findByOrderNumberAndType(ORDER_NUMBER, PointUseType.USE)).willReturn(Optional.of(otherUse));

            assertThatThrownBy(() -> service.cancelUse(
                    new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 100L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_NOT_FOUND);
        }

        @Test
        @DisplayName("취소 가능 잔액(USE.amount - sumCancelled) 을 초과하면 USE_CANCEL_AMOUNT_EXCEEDED 가 발생하고 cancel row 를 저장하지 않는다")
        void over_cancellable_amount_throws_exceeded() {
            PointUse use = PointUse.use(USER_ID, ORDER_NUMBER, 1000L);
            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.empty());
            given(useRepository.findByOrderNumberAndType(ORDER_NUMBER, PointUseType.USE)).willReturn(Optional.of(use));
            // 이미 700 환불됨 → 잔여 300
            given(useRepository.sumCancelledByUseId(use.getId())).willReturn(700L);
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));

            assertThatThrownBy(() -> service.cancelUse(
                    new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 400L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);

            verify(useRepository, never()).save(any(PointUse.class));
            verify(earnRepository, never()).save(any(PointEarn.class));
        }
    }

    @Nested
    @DisplayName("환불 분배")
    class Distribution {

        @Test
        @DisplayName("원본 적립이 살아있으면 PointEarn.restoreFromUseCancel 가 호출되고 새 적립 발급은 일어나지 않는다")
        void restores_to_alive_origin_earn() {
            PointUse use = PointUse.use(USER_ID, ORDER_NUMBER, 1000L);
            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.empty());
            given(useRepository.findByOrderNumberAndType(ORDER_NUMBER, PointUseType.USE)).willReturn(Optional.of(use));
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.save(any(PointUse.class))).willAnswer(inv -> inv.getArgument(0));

            PointEarn aliveEarn = mock(PointEarn.class);
            given(aliveEarn.getId()).willReturn(50L);
            given(aliveEarn.getUserId()).willReturn(USER_ID);
            given(aliveEarn.isAlive(any())).willReturn(true);

            given(earnRepository.findAllById(List.of(50L))).willReturn(List.of(aliveEarn));
            given(useDetailRepository.findByUseIdOrderByIdAsc(nullable(Long.class)))
                .willReturn(List.of(PointUseDetail.of(null, 50L, 1000L)));
            given(useDetailRepository.findCancelDetailsByOriginalUseId(nullable(Long.class)))
                .willReturn(List.of());

            CancelUsePointResult result = service.cancelUse(
                new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 600L));

            verify(aliveEarn).restoreFromUseCancel(600L);
            verify(earnRepository, never()).save(any(PointEarn.class));
            assertThat(result.amount()).isEqualTo(600L);
            assertThat(result.remainingCancellable()).isEqualTo(400L); // use=1000, refund=600
        }

        @Test
        @DisplayName("원본 적립이 만료되었으면 PointEarn.reissueFromUseCancel 로 신규 적립이 발급되고 원본의 restore 는 호출되지 않는다")
        void reissues_when_origin_earn_expired() {
            PointUse use = PointUse.use(USER_ID, ORDER_NUMBER, 1000L);
            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.empty());
            given(useRepository.findByOrderNumberAndType(ORDER_NUMBER, PointUseType.USE)).willReturn(Optional.of(use));
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.save(any(PointUse.class))).willAnswer(inv -> inv.getArgument(0));

            PointEarn expiredEarn = mock(PointEarn.class);
            given(expiredEarn.getId()).willReturn(50L);
            given(expiredEarn.getUserId()).willReturn(USER_ID);
            given(expiredEarn.isAlive(any())).willReturn(false);

            given(earnRepository.findAllById(List.of(50L))).willReturn(List.of(expiredEarn));
            given(useDetailRepository.findByUseIdOrderByIdAsc(nullable(Long.class)))
                .willReturn(List.of(PointUseDetail.of(null, 50L, 1000L)));
            given(useDetailRepository.findCancelDetailsByOriginalUseId(nullable(Long.class)))
                .willReturn(List.of());

            PointEarn reissued = mock(PointEarn.class);
            given(reissued.getId()).willReturn(999L);
            given(earnRepository.save(any(PointEarn.class))).willReturn(reissued);

            given(policy.useCancelReissueDays()).willReturn(365);

            CancelUsePointResult result = service.cancelUse(
                new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 600L));

            verify(earnRepository).save(any(PointEarn.class));
            verify(expiredEarn, never()).restoreFromUseCancel(anyLong());
            assertThat(result.amount()).isEqualTo(600L);
            assertThat(result.remainingCancellable()).isEqualTo(400L); // use=1000, refund=600
        }

        @Test
        @DisplayName("이전 부분환불 detail 들이 있으면 그만큼을 차감한 잔여 환불 가능 분량으로 다음 detail 부터 분배된다")
        void respects_previously_refunded_amounts_per_earn() {
            PointUse use = PointUse.use(USER_ID, ORDER_NUMBER, 1500L);

            given(useRepository.findByOrderRefundId(ORDER_REFUND_ID)).willReturn(Optional.empty());
            given(useRepository.findByOrderNumberAndType(ORDER_NUMBER, PointUseType.USE)).willReturn(Optional.of(use));
            // USE row 는 immutable — 누적 환불액은 SUM 으로 도출
            given(useRepository.sumCancelledByUseId(use.getId())).willReturn(800L);
            given(userRepository.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(mock(PointUser.class)));
            given(useRepository.save(any(PointUse.class))).willAnswer(inv -> inv.getArgument(0));

            given(useDetailRepository.findByUseIdOrderByIdAsc(nullable(Long.class)))
                .willReturn(List.of(
                    PointUseDetail.of(null, 50L, 1000L),
                    PointUseDetail.of(null, 51L, 500L)
                ));
            given(useDetailRepository.findCancelDetailsByOriginalUseId(nullable(Long.class)))
                .willReturn(List.of(PointUseDetail.of(99L, 50L, 800L)));

            PointEarn earn50 = mock(PointEarn.class);
            given(earn50.getId()).willReturn(50L);
            given(earn50.getUserId()).willReturn(USER_ID);
            given(earn50.isAlive(any())).willReturn(true);

            PointEarn earn51 = mock(PointEarn.class);
            given(earn51.getId()).willReturn(51L);
            given(earn51.getUserId()).willReturn(USER_ID);
            given(earn51.isAlive(any())).willReturn(true);

            given(earnRepository.findAllById(List.of(50L, 51L))).willReturn(List.of(earn50, earn51));

            CancelUsePointResult result = service.cancelUse(
                new CancelUsePointCommand(USER_ID, ORDER_NUMBER, ORDER_REFUND_ID, 600L));

            verify(earn50).restoreFromUseCancel(200L);
            verify(earn51).restoreFromUseCancel(400L);
            assertThat(result.amount()).isEqualTo(600L);
            assertThat(result.remainingCancellable()).isEqualTo(100L); // use=1500, alreadyCancelled=800, +600=1400
        }
    }
}
