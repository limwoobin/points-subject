package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.enums.EarnType;
import com.example.pointssubject.domain.enums.PointUseType;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUseRepository;
import com.example.pointssubject.service.command.dto.CancelUsePointCommand;
import com.example.pointssubject.service.command.dto.CancelUsePointResult;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import com.example.pointssubject.service.command.dto.UsePointCommand;
import com.example.pointssubject.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CancelUsePointCommandServiceTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointEarnCommandService earnService;
    @Autowired private PointUseCommandService useService;
    @Autowired private PointEarnRepository earnRepository;
    @Autowired private PointUseRepository useRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("기본 사용취소 흐름")
    class BasicCancel {

        @Test
        @DisplayName("부분 사용취소 시 살아있는 적립의 remaining_amount 가 환불 금액만큼 복원되고, 원본 USE 누적 환불액이 갱신된다")
        void partial_cancel_restores_alive_earn() {
            EarnPointResult earned = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-CK1", 600L));

            CancelUsePointResult result = useService.cancelUse(
                new CancelUsePointCommand(USER_ID, "ORD-CK1", "ORF-1", 200L));

            assertThat(result.amount()).isEqualTo(200L);
            assertThat(result.remainingCancellable()).isEqualTo(400L);

            entityManager.flush();
            entityManager.clear();
            // 사용 1000 - 600 + 환불 200 = 600
            assertThat(earnRepository.findById(earned.earnId()).orElseThrow().getRemainingAmount())
                .isEqualTo(600L);
            assertThat(earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now())).isEqualTo(600L);
        }

        @Test
        @DisplayName("전체 사용취소 시 USE 누적 환불액이 사용 금액과 같아지고 적립 remaining 도 사용 전 상태로 복원된다")
        void full_cancel_marks_use_fully_cancelled() {
            EarnPointResult earned = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-FULL", 600L));

            CancelUsePointResult result = useService.cancelUse(
                new CancelUsePointCommand(USER_ID, "ORD-FULL", "ORF-FULL", 600L));

            assertThat(result.amount()).isEqualTo(600L);
            assertThat(result.remainingCancellable()).isZero();

            entityManager.flush();
            entityManager.clear();
            assertThat(earnRepository.findById(earned.earnId()).orElseThrow().getRemainingAmount())
                .isEqualTo(1000L);
        }

        @Test
        @DisplayName("A(1000)+B(500) 후 1200 사용, 1100 환불 시 A 1000 + B 100 환불되고 A.remaining=1000, B.remaining=400")
        void partial_refund_1100_restores_A_fully_then_B() {
            EarnPointResult a = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            EarnPointResult b = earnService.earn(new EarnPointCommand(USER_ID, 500L, null));
            useService.use(new UsePointCommand(USER_ID, "A1234", 1200L));

            CancelUsePointResult d = useService.cancelUse(
                new CancelUsePointCommand(USER_ID, "A1234", "REFUND-D", 1100L));

            assertThat(d.amount()).isEqualTo(1100L);
            assertThat(d.remainingCancellable()).isEqualTo(100L);

            entityManager.flush();
            entityManager.clear();
            assertThat(earnRepository.findById(a.earnId()).orElseThrow().getRemainingAmount())
                .isEqualTo(1000L);
            assertThat(earnRepository.findById(b.earnId()).orElseThrow().getRemainingAmount())
                .isEqualTo(400L);
        }
    }

    @Nested
    @DisplayName("만료된 적립 재발급")
    class ReissueWhenExpired {

        @Test
        @DisplayName("사용 후 원본 적립이 만료된 시점에 사용취소를 하면 환불 금액만큼 새 적립이 USE_CANCEL_REISSUE 로 발급된다")
        void expired_origin_earn_is_reissued_as_new_earn() {
            EarnPointResult earned = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-EXP", 1000L));

            entityManager.createQuery("UPDATE PointEarn e SET e.expiresAt = :past WHERE e.id = :id")
                .setParameter("past", LocalDateTime.now().minusDays(1))
                .setParameter("id", earned.earnId())
                .executeUpdate();
            entityManager.clear();

            CancelUsePointResult result = useService.cancelUse(
                new CancelUsePointCommand(USER_ID, "ORD-EXP", "ORF-EXP", 200L));

            entityManager.flush();
            entityManager.clear();
            // cancelId 가 발행한 reissue 적립 1건 — origin/금액 검증
            var reissuedList = earnRepository.findByOriginUseCancelId(result.cancelId());
            assertThat(reissuedList).hasSize(1);
            PointEarn reissued = reissuedList.get(0);
            assertThat(reissued.getId()).isNotEqualTo(earned.earnId());
            assertThat(reissued.getInitialAmount()).isEqualTo(200L);
            assertThat(reissued.getRemainingAmount()).isEqualTo(200L);
            assertThat(reissued.getType()).isEqualTo(EarnType.USE_CANCEL_REISSUE);
            assertThat(earnRepository.findById(earned.earnId()).orElseThrow().getRemainingAmount())
                .isZero();
        }
    }

    @Nested
    @DisplayName("멱등성 (orderRefundId) — reject 패턴")
    class OrderRefundIdIdempotency {

        @Test
        @DisplayName("이미 처리된 orderRefundId 로 재요청하면 페이로드 동일 여부 무관하게 ORDER_REFUND_ID_DUPLICATED 가 발생하고 추가 환불은 일어나지 않는다")
        void duplicate_refund_id_throws_and_no_extra_cancel_row() {
            earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-IDEM", 600L));

            useService.cancelUse(new CancelUsePointCommand(USER_ID, "ORD-IDEM", "ORF-IDEM", 300L));

            // 같은 페이로드 재요청도 reject
            assertThatThrownBy(() -> useService.cancelUse(
                    new CancelUsePointCommand(USER_ID, "ORD-IDEM", "ORF-IDEM", 300L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.ORDER_REFUND_ID_DUPLICATED);

            // 다른 amount 재요청도 reject
            assertThatThrownBy(() -> useService.cancelUse(
                    new CancelUsePointCommand(USER_ID, "ORD-IDEM", "ORF-IDEM", 200L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.ORDER_REFUND_ID_DUPLICATED);

            // USE_CANCEL row 는 첫 호출 1건만 존재
            assertThat(useRepository.findByOrderNumber("ORD-IDEM").stream()
                .filter(u -> u.getType() == PointUseType.USE_CANCEL).count()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("초과 환불 차단")
    class OverRefund {

        @Test
        @DisplayName("부분 환불 누적이 사용 금액을 초과하지 않도록, 잔여 환불 가능 분량을 넘는 요청은 USE_CANCEL_AMOUNT_EXCEEDED 로 거부된다")
        void cumulative_partial_refunds_cannot_exceed_use_amount() {
            earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-OVER", 600L));

            useService.cancelUse(new CancelUsePointCommand(USER_ID, "ORD-OVER", "ORF-1", 400L));
            useService.cancelUse(new CancelUsePointCommand(USER_ID, "ORD-OVER", "ORF-2", 100L));
            assertThatThrownBy(() -> useService.cancelUse(
                    new CancelUsePointCommand(USER_ID, "ORD-OVER", "ORF-3", 200L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_CANCEL_AMOUNT_EXCEEDED);
        }
    }
}
