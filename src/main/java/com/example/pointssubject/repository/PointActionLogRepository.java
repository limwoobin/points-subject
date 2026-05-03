package com.example.pointssubject.repository;

import com.example.pointssubject.domain.entity.PointActionLog;
import com.example.pointssubject.domain.enums.PointActionType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointActionLogRepository extends JpaRepository<PointActionLog, Long> {

    Page<PointActionLog> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    List<PointActionLog> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<PointActionLog> findByUserIdAndActionTypeOrderByCreatedAtAsc(Long userId, PointActionType actionType);
}
