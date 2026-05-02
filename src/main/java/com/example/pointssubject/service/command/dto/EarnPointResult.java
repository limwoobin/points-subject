package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.PointSource;
import java.time.LocalDateTime;

public record EarnPointResult(
    Long earnId,
    Long userId,
    Long amount,
    PointSource source,
    LocalDateTime expiresAt
) {

    public static EarnPointResult from(PointEarn earn) {
        return new EarnPointResult(
            earn.getId(),
            earn.getUserId(),
            earn.getInitialAmount(),
            earn.getSource(),
            earn.getExpiresAt()
        );
    }
}
