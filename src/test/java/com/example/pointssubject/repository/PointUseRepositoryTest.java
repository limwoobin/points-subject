package com.example.pointssubject.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.config.JpaAuditingConfig;
import com.example.pointssubject.domain.entity.PointUse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class PointUseRepositoryTest {

    @Autowired private PointUseRepository useRepository;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("orderNumber 로 조회 시 동일한 orderNumber 의 row 가 존재하면 entity 가 담긴 Optional 이 반환된다")
    void find_by_order_number_returns_existing_row() {
        useRepository.saveAndFlush(PointUse.use(1L, "ORD-FIND", 1000L));

        assertThat(useRepository.findByOrderNumber("ORD-FIND")).isPresent();
    }

    @Test
    @DisplayName("orderNumber 로 조회 시 일치하는 row 가 없으면 Optional.empty 가 반환된다")
    void find_by_order_number_returns_empty_when_absent() {
        assertThat(useRepository.findByOrderNumber("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("save 직후 cancelledAmount 는 0, audit 컬럼은 자동 채워진다")
    void persisted_use_has_zero_cancelled_amount_and_audit() {
        PointUse saved = useRepository.saveAndFlush(PointUse.use(1L, "ORD-AUDIT", 1000L));
        em.clear();

        PointUse refreshed = useRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.getCancelledAmount()).isZero();
        assertThat(refreshed.getCreatedAt()).isNotNull();
        assertThat(refreshed.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(refreshed.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("softDelete() 호출 후 findById 로 조회하면 @SQLRestriction 에 의해 빈 Optional 이 반환된다")
    void soft_deleted_use_invisible() {
        PointUse saved = useRepository.saveAndFlush(PointUse.use(1L, "ORD-SOFT", 1000L));
        saved.softDelete();
        em.flush();
        em.clear();

        assertThat(useRepository.findById(saved.getId())).isEmpty();
    }
}
