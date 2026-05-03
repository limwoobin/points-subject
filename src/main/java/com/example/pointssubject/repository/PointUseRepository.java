package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.enums.PointUseType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUseRepository extends JpaRepository<PointUse, Long> {

    List<PointUse> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumberAndType(String orderNumber, PointUseType type);

    Optional<PointUse> findByOrderNumberAndType(String orderNumber, PointUseType type);

    Optional<PointUse> findByOrderRefundId(String orderRefundId);

    /**
     * 한 USE row 가 누적 환불받은 총액 — `cancellable = USE.amount - sumCancelled` 계산용.
     * USE row 는 immutable 이고 cancelled_amount 컬럼이 없기 때문에 SUM 으로 도출
     */
    @Query("""
        SELECT COALESCE(SUM(c.amount), 0)
        FROM PointUse c
        WHERE c.targetUseId = :useId
          AND c.type = com.example.pointssubject.domain.enums.PointUseType.USE_CANCEL
        """)
    long sumCancelledByUseId(@Param("useId") Long useId);
}
