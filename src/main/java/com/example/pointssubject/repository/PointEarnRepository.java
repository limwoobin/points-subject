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

    /** 필터만 SQL — 우선순위 정렬은 호출자가 PointEarn.USE_PRIORITY 로 application 레이어에서 수행. */
    @Query("""
        SELECT e
        FROM PointEarn e
        WHERE e.userId = :userId
          AND e.status = com.example.pointssubject.domain.enums.EarnStatus.ACTIVE
          AND e.remainingAmount > 0
          AND e.expiresAt > :now
        """)
    List<PointEarn> findActiveCandidatesForUse(@Param("userId") Long userId,
                                               @Param("now") LocalDateTime now);

    /** 한 사용취소 트랜잭션이 발행한 USE_CANCEL_REISSUE 적립 — 만료된 분배분에 대한 신규 적립 추적용. */
    List<PointEarn> findByOriginUseCancelId(Long originUseCancelId);
}
