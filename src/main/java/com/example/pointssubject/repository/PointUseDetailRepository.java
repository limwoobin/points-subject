package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointUseDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUseDetailRepository extends JpaRepository<PointUseDetail, Long> {

    /** 원본 USE 의 detail 을 분배 순서(=id 오름차순) 로 조회 — FIFO 환불 순회용. */
    List<PointUseDetail> findByUseIdOrderByIdAsc(Long useId);

    /** 부분 환불 누적 시 earnId 별 이미 환불된 양 계산용. */
    @Query("""
        SELECT d
        FROM PointUseDetail d
        WHERE d.useId IN (
            SELECT pu.id FROM PointUse pu
            WHERE pu.targetUseId = :originalUseId
              AND pu.type = com.example.pointssubject.domain.enums.PointUseType.USE_CANCEL
        )
        """)
    List<PointUseDetail> findCancelDetailsByOriginalUseId(@Param("originalUseId") Long originalUseId);
}
