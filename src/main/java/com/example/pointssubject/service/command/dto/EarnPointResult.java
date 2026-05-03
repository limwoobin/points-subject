package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnType;
import java.time.LocalDateTime;

public record EarnPointResult(
    Long earnId,
    Long userId,
    Long amount,
    EarnType type,
    LocalDateTime expiresAt
) {

    public static EarnPointResult from(PointEarn earn) {
        return new EarnPointResult(
            earn.getId(),
            earn.getUserId(),
            earn.getInitialAmount(),
            earn.getType(),
            earn.getExpiresAt()
        );
    }
}
