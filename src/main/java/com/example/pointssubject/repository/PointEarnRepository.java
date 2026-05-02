package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnStatus;
import java.time.LocalDateTime;
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
}
