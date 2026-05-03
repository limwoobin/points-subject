package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.entity.PointUse;
import java.time.LocalDateTime;

public record CancelUsePointResult(
    Long cancelId,
    Long amount,
    Long remainingCancellable,
    LocalDateTime cancelledAt
) {

    public static CancelUsePointResult from(PointUse cancelRow, long remainingCancellable) {
        return new CancelUsePointResult(
            cancelRow.getId(),
            cancelRow.getAmount(),
            remainingCancellable,
            cancelRow.getCreatedAt()
        );
    }
}
