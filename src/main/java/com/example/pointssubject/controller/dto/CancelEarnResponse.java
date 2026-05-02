package com.example.pointssubject.controller.dto;

import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.service.command.dto.CancelEarnResult;
import java.time.LocalDateTime;

public record CancelEarnResponse(
    Long earnId,
    Long userId,
    EarnStatus status,
    LocalDateTime cancelledAt
) {

    public static CancelEarnResponse from(CancelEarnResult result) {
        return new CancelEarnResponse(
            result.earnId(),
            result.userId(),
            result.status(),
            result.cancelledAt()
        );
    }
}
