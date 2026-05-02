package com.example.pointssubject.controller.dto;

import com.example.pointssubject.domain.enums.PointSource;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import java.time.LocalDateTime;

public record EarnPointResponse(
    Long earnId,
    Long userId,
    Long amount,
    PointSource source,
    LocalDateTime expiresAt
) {

    public static EarnPointResponse from(EarnPointResult result) {
        return new EarnPointResponse(
            result.earnId(),
            result.userId(),
            result.amount(),
            result.source(),
            result.expiresAt()
        );
    }
}
