package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointUser;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PointUserRepository extends JpaRepository<PointUser, Long> {

    Optional<PointUser> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM PointUser u WHERE u.userId = :userId")
    Optional<PointUser> findByUserIdForUpdate(@Param("userId") Long userId);
}
