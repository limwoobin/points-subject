package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.CancelUsePointResult;
import java.time.LocalDateTime;

public record CancelUsePointResponse(
    Long cancelId,
    Long amount,
    Long remainingCancellable,
    LocalDateTime cancelledAt
) {

    public static CancelUsePointResponse from(CancelUsePointResult result) {
        return new CancelUsePointResponse(
            result.cancelId(),
            result.amount(),
            result.remainingCancellable(),
            result.cancelledAt()
        );
    }
}
