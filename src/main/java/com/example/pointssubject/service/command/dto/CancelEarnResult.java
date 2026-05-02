package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnStatus;
import java.time.LocalDateTime;

public record CancelEarnResult(
    Long earnId,
    Long userId,
    EarnStatus status,
    LocalDateTime cancelledAt
) {

    public static CancelEarnResult from(PointEarn earn) {
        return new CancelEarnResult(
            earn.getId(),
            earn.getUserId(),
            earn.getStatus(),
            earn.getCancelledAt()
        );
    }
}
