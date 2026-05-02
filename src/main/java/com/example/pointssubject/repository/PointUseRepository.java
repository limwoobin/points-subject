package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointUse;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointUseRepository extends JpaRepository<PointUse, Long> {

    Optional<PointUse> findByOrderNumber(String orderNumber);
}
