package com.example.pointssubject.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.config.JpaAuditingConfig;
import com.example.pointssubject.domain.entity.PointUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PointUserRepositoryTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointUserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

    @Nested
    @DisplayName("findByUserIdForUpdate() — 비관 락 조회")
    class FindByUserIdForUpdate {

        @Test
        @DisplayName("회원 row 가 없으면 Optional.empty 가 반환된다")
        void returns_empty_when_absent() {
            assertThat(userRepository.findByUserIdForUpdate(USER_ID)).isEmpty();
        }

        @Test
        @DisplayName("회원 row 가 존재하면 entity 가 담긴 Optional 이 반환된다")
        void returns_user_when_exists() {
            userRepository.saveAndFlush(PointUser.create(USER_ID));

            assertThat(userRepository.findByUserIdForUpdate(USER_ID))
                .isPresent()
                .get()
                .extracting(PointUser::getUserId)
                .isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("soft-delete 된 회원은 @SQLRestriction 에 의해 조회되지 않는다")
        void soft_deleted_user_invisible() {
            PointUser user = userRepository.saveAndFlush(PointUser.create(USER_ID));
            user.softDelete();
            em.flush();
            em.clear();

            assertThat(userRepository.findByUserIdForUpdate(USER_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Hibernate 어노테이션 동작")
    class HibernateAnnotations {

        @Test
        @DisplayName("회원을 save 하면 audit 컬럼(createdAt/By, updatedAt/By) 이 자동 채워지고 maxBalance 는 null 로 시작된다")
        void audit_columns_populated_on_save() {
            PointUser saved = userRepository.saveAndFlush(PointUser.create(USER_ID));
            em.clear();

            PointUser refreshed = userRepository.findById(USER_ID).orElseThrow();
            assertThat(refreshed.getCreatedAt()).isNotNull();
            assertThat(refreshed.getCreatedBy()).isEqualTo("SYSTEM");
            assertThat(refreshed.getUpdatedAt()).isNotNull();
            assertThat(refreshed.getDeletedAt()).isNull();
            assertThat(saved.getMaxBalance()).isNull();
        }

        @Test
        @DisplayName("updateMaxBalance 호출 후 flush 하면 updatedAt 이 갱신되고 max_balance 컬럼이 새 값으로 반영된다")
        void update_changes_updated_at() {
            PointUser saved = userRepository.saveAndFlush(PointUser.create(USER_ID));
            em.flush();
            em.clear();
            PointUser fetched = userRepository.findById(USER_ID).orElseThrow();
            var updatedBefore = fetched.getUpdatedAt();

            fetched.updateMaxBalance(5000L);
            em.flush();
            em.clear();

            PointUser afterUpdate = userRepository.findById(USER_ID).orElseThrow();
            assertThat(afterUpdate.getMaxBalance()).isEqualTo(5000L);
            assertThat(afterUpdate.getUpdatedAt()).isAfterOrEqualTo(updatedBefore);
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("softDelete() 호출 후 findById 로 조회하면 @SQLRestriction 에 의해 빈 Optional 이 반환된다")
        void soft_deleted_invisible_to_findById() {
            PointUser user = userRepository.saveAndFlush(PointUser.create(USER_ID));
            user.softDelete();
            em.flush();
            em.clear();

            assertThat(userRepository.findById(USER_ID)).isEmpty();
        }
    }
}
