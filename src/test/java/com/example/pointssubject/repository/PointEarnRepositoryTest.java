package com.example.pointssubject.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.config.JpaAuditingConfig;
import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.PointSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PointEarnRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired private PointEarnRepository earnRepository;

    @PersistenceContext
    private EntityManager em;

    @Nested
    @DisplayName("sumActiveBalance() — 잔액 합산 쿼리")
    class SumActiveBalance {

        @Test
        @DisplayName("적립 row 가 하나도 없으면 NULL 이 아니라 0 을 반환한다 (COALESCE 동작)")
        void returns_zero_when_no_rows() {
            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());
            assertThat(sum).isZero();
        }

        @Test
        @DisplayName("ACTIVE 이고 미만료인 단일 row 만 있으면 그 row 의 remaining_amount 가 그대로 반환된다")
        void counts_single_active_earn() {
            saveEarn(USER_ID, 1000L, daysFromNow(30));

            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());

            assertThat(sum).isEqualTo(1000L);
        }

        @Test
        @DisplayName("ACTIVE 이고 미만료인 row 가 여러 개면 각 row 의 remaining_amount 합계가 반환된다")
        void sums_multiple_active_earns() {
            saveEarn(USER_ID, 1000L, daysFromNow(30));
            saveEarn(USER_ID, 500L, daysFromNow(60));
            saveEarn(USER_ID, 250L, daysFromNow(90));

            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());

            assertThat(sum).isEqualTo(1750L);
        }

        @Test
        @DisplayName("만료된 적립 (expires_at <= now) 은 합산에서 제외된다")
        void excludes_expired_earn() {
            saveEarn(USER_ID, 1000L, daysFromNow(30));
            saveEarn(USER_ID, 500L, daysFromNow(-1)); // already expired

            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());

            assertThat(sum).isEqualTo(1000L);
        }

        @Test
        @DisplayName("취소된 적립 (status=CANCELLED) 은 합산에서 제외된다")
        void excludes_cancelled_earn() {
            PointEarn alive = saveEarn(USER_ID, 1000L, daysFromNow(30));
            PointEarn toCancel = saveEarn(USER_ID, 500L, daysFromNow(30));
            toCancel.cancel(LocalDateTime.now());
            em.flush();
            em.clear();

            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());

            assertThat(sum).isEqualTo(1000L);
            assertThat(alive.getId()).isNotNull();
        }

        @Test
        @DisplayName("soft-delete 된 적립은 @SQLRestriction 에 의해 합산에서 제외된다")
        void excludes_soft_deleted_earn() {
            saveEarn(USER_ID, 1000L, daysFromNow(30));
            PointEarn toDelete = saveEarn(USER_ID, 500L, daysFromNow(30));
            toDelete.softDelete();
            em.flush();
            em.clear();

            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());

            assertThat(sum).isEqualTo(1000L);
        }

        @Test
        @DisplayName("쿼리는 user_id 로 필터링하므로 다른 회원의 적립은 합산되지 않는다")
        void excludes_other_users_earns() {
            saveEarn(USER_ID, 1000L, daysFromNow(30));
            saveEarn(OTHER_USER_ID, 9999L, daysFromNow(30));

            long sum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());

            assertThat(sum).isEqualTo(1000L);
        }

        @Test
        @DisplayName("동일 row 라도 now 인자에 따라 만료 판정이 달라져 합산 결과가 변한다")
        void now_parameter_drives_expiry_filter() {
            saveEarn(USER_ID, 1000L, daysFromNow(10));

            // 미래 시점에서 조회 → 그 시점엔 이미 만료
            long futureSum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now().plusDays(20));
            assertThat(futureSum).isZero();

            // 현재 시점에선 살아있음
            long nowSum = earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now());
            assertThat(nowSum).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("Hibernate 어노테이션 동작")
    class HibernateAnnotations {

        @Test
        @DisplayName("적립을 save 하면 BaseEntity 의 audit 컬럼(createdAt/By, updatedAt/By) 이 자동 채워진다")
        void audit_columns_populated_on_save() {
            PointEarn saved = saveEarn(USER_ID, 1000L, daysFromNow(30));
            em.flush();
            em.clear();

            PointEarn refreshed = earnRepository.findById(saved.getId()).orElseThrow();
            assertThat(refreshed.getCreatedAt()).isNotNull();
            assertThat(refreshed.getCreatedBy()).isEqualTo("SYSTEM");
            assertThat(refreshed.getUpdatedAt()).isNotNull();
            assertThat(refreshed.getUpdatedBy()).isEqualTo("SYSTEM");
            assertThat(refreshed.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("softDelete() 호출 후 findById 로 조회하면 @SQLRestriction 에 의해 빈 Optional 이 반환된다")
        void soft_deleted_row_invisible_to_findById() {
            PointEarn saved = saveEarn(USER_ID, 1000L, daysFromNow(30));
            saved.softDelete();
            em.flush();
            em.clear();

            assertThat(earnRepository.findById(saved.getId())).isEmpty();
        }
    }

    private PointEarn saveEarn(Long userId, long amount, LocalDateTime expiresAt) {
        PointEarn earn = PointEarn.earn(userId, amount, PointSource.SYSTEM, expiresAt);
        return earnRepository.saveAndFlush(earn);
    }

    private static LocalDateTime daysFromNow(long days) {
        return LocalDateTime.now().plusDays(days);
    }
}
