package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointEarnRepository extends JpaRepository<PointEarn, Long> {

    @Query("""
        SELECT COALESCE(SUM(e.remainingAmount), 0)
        FROM PointEarn e
        WHERE e.userId = :userId
          AND e.status = :status
          AND e.expiresAt > :now
        """)
    long sumActiveBalance(@Param("userId") Long userId,
                          @Param("status") EarnStatus status,
                          @Param("now") LocalDateTime now);

    default long sumActiveBalance(Long userId, LocalDateTime now) {
        return sumActiveBalance(userId, EarnStatus.ACTIVE, now);
    }

    /**
     * 사용 후보 적립 조회 — PRD §3.3.3 의 우선순위:
     * <ol>
     *   <li>수기(MANUAL) 우선</li>
     *   <li>만료 임박 (expires_at ASC)</li>
     *   <li>적립일 빠른 순 (created_at ASC)</li>
     *   <li>id ASC (결정성 보장)</li>
     * </ol>
     */
    @Query("""
        SELECT e
        FROM PointEarn e
        WHERE e.userId = :userId
          AND e.status = com.example.pointssubject.domain.enums.EarnStatus.ACTIVE
          AND e.remainingAmount > 0
          AND e.expiresAt > :now
        ORDER BY
          CASE WHEN e.source = com.example.pointssubject.domain.enums.PointSource.MANUAL THEN 0 ELSE 1 END,
          e.expiresAt ASC,
          e.createdAt ASC,
          e.id ASC
        """)
    List<PointEarn> findActiveCandidatesForUse(@Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);
}
