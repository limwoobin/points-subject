package com.example.pointssubject.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.config.JpaAuditingConfig;
import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.enums.PointUseType;
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
    @DisplayName("findByOrderNumber 는 같은 주문의 USE 와 USE_CANCEL 행을 모두 반환한다 (Option B 단일 테이블)")
    void find_by_order_number_returns_all_rows_for_an_order() {
        PointUse use = useRepository.saveAndFlush(PointUse.use(1L, "ORD-FIND", 1000L));
        useRepository.saveAndFlush(PointUse.useCancel(1L, "ORD-FIND", use.getId(), "ORF-1", 600L));
        em.clear();

        assertThat(useRepository.findByOrderNumber("ORD-FIND"))
            .extracting(PointUse::getType)
            .containsExactlyInAnyOrder(PointUseType.USE, PointUseType.USE_CANCEL);
    }

    @Test
    @DisplayName("findByOrderNumber 는 일치하는 row 가 없으면 빈 리스트를 반환한다")
    void find_by_order_number_returns_empty_list_when_absent() {
        assertThat(useRepository.findByOrderNumber("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("existsByOrderNumberAndType 으로 USE 행 단건 멱등성을 판별할 수 있다 (USE_CANCEL 행은 무관)")
    void exists_by_order_number_and_type_distinguishes_use_from_cancel() {
        PointUse use = useRepository.saveAndFlush(PointUse.use(1L, "ORD-EXIST", 1000L));
        useRepository.saveAndFlush(PointUse.useCancel(1L, "ORD-EXIST", use.getId(), "ORF-X", 100L));

        assertThat(useRepository.existsByOrderNumberAndType("ORD-EXIST", PointUseType.USE)).isTrue();
        assertThat(useRepository.existsByOrderNumberAndType("ORD-NEW", PointUseType.USE)).isFalse();
    }

    @Test
    @DisplayName("findByOrderRefundId 는 같은 환불 ID 의 USE_CANCEL row 를 단건 반환한다 (멱등 분기용)")
    void find_by_order_refund_id_returns_existing_cancel() {
        PointUse use = useRepository.saveAndFlush(PointUse.use(1L, "ORD-RF", 1000L));
        useRepository.saveAndFlush(PointUse.useCancel(1L, "ORD-RF", use.getId(), "ORF-001", 500L));

        assertThat(useRepository.findByOrderRefundId("ORF-001")).isPresent();
        assertThat(useRepository.findByOrderRefundId("ORF-MISSING")).isEmpty();
    }

    @Test
    @DisplayName("save 직후 audit 컬럼이 자동 채워지고 sumCancelledByUseId 는 0 이다 (USE row 는 immutable, 누적 환불액은 SUM 도출)")
    void persisted_use_starts_with_zero_cancelled_sum() {
        PointUse saved = useRepository.saveAndFlush(PointUse.use(1L, "ORD-AUDIT", 1000L));
        em.clear();

        PointUse refreshed = useRepository.findById(saved.getId()).orElseThrow();
        assertThat(refreshed.getType()).isEqualTo(PointUseType.USE);
        assertThat(refreshed.getCreatedAt()).isNotNull();
        assertThat(refreshed.getCreatedBy()).isEqualTo("SYSTEM");
        assertThat(refreshed.getDeletedAt()).isNull();

        assertThat(useRepository.sumCancelledByUseId(saved.getId())).isZero();
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
