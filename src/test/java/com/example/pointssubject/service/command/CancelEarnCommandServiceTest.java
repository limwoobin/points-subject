package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.service.command.dto.CancelEarnCommand;
import com.example.pointssubject.service.command.dto.CancelEarnResult;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import com.example.pointssubject.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CancelEarnCommandServiceTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointEarnCommandService earnService;
    @Autowired private PointEarnRepository earnRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("적립취소 성공")
    class Success {

        @Test
        @DisplayName("적립 직후 취소를 호출하면 status 가 CANCELLED 로, remaining 이 0 으로, cancelledAt 이 현재시각으로 갱신된다")
        void cancel_just_after_earn() {
            EarnPointResult earned = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));

            CancelEarnResult result = earnService.cancelEarn(new CancelEarnCommand(earned.earnId()));

            assertThat(result.earnId()).isEqualTo(earned.earnId());
            assertThat(result.status()).isEqualTo(EarnStatus.CANCELLED);
            assertThat(result.cancelledAt()).isNotNull();

            // DB 반영 확인 (영속성 컨텍스트 클리어 후 재조회)
            entityManager.flush();
            entityManager.clear();
            PointEarn refreshed = earnRepository.findById(earned.earnId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(EarnStatus.CANCELLED);
            assertThat(refreshed.getRemainingAmount()).isEqualTo(0L);
            assertThat(refreshed.getCancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("적립을 취소하면 해당 적립 금액만큼 회원 잔액 합계에서 제외된다 (status=CANCELLED 필터)")
        void cancelled_earn_excluded_from_balance() {
            EarnPointResult e1 = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            earnService.earn(new EarnPointCommand(USER_ID, 500L, null));

            assertThat(earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now()))
                .isEqualTo(1500L);

            earnService.cancelEarn(new CancelEarnCommand(e1.earnId()));

            assertThat(earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now()))
                .isEqualTo(500L);
        }

        @Test
        @DisplayName("적립취소 결과는 원본 적립의 earnId 를 그대로 가리킨다 (별도 PK 발급 없음)")
        void cancel_reuses_original_earn_id() {
            EarnPointResult earned = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));

            CancelEarnResult result = earnService.cancelEarn(new CancelEarnCommand(earned.earnId()));

            assertThat(result.earnId()).isEqualTo(earned.earnId());
        }

        @Test
        @DisplayName("만료된 적립이라도 미사용 상태이면 취소가 가능하다 (만료/취소는 독립 컬럼)")
        void expired_earn_can_still_be_cancelled() {
            EarnPointResult earned = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));

            // expires_at 을 과거로 강제 — 시간 경과 시뮬레이션
            entityManager.createQuery(
                    "UPDATE PointEarn e SET e.expiresAt = :past WHERE e.id = :id")
                .setParameter("past", LocalDateTime.now().minusDays(1))
                .setParameter("id", earned.earnId())
                .executeUpdate();
            entityManager.clear();

            CancelEarnResult result = earnService.cancelEarn(new CancelEarnCommand(earned.earnId()));
            assertThat(result.status()).isEqualTo(EarnStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("audit 컬럼 갱신")
    class Audit {

        @Test
        @DisplayName("적립을 취소하면 updatedAt / updatedBy 가 갱신된다")
        void audit_columns_updated_on_cancel() {
            EarnPointResult earned = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));

            entityManager.flush();
            entityManager.clear();
            PointEarn beforeCancel = earnRepository.findById(earned.earnId()).orElseThrow();
            LocalDateTime updatedBefore = beforeCancel.getUpdatedAt();

            earnService.cancelEarn(new CancelEarnCommand(earned.earnId()));

            entityManager.flush();
            entityManager.clear();
            PointEarn afterCancel = earnRepository.findById(earned.earnId()).orElseThrow();
            assertThat(afterCancel.getUpdatedAt()).isAfterOrEqualTo(updatedBefore);
            assertThat(afterCancel.getUpdatedBy()).isNotNull();
        }
    }
}
