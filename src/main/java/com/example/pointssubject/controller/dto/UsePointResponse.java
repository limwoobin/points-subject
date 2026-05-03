package com.example.pointssubject.controller.dto;

import com.example.pointssubject.domain.UseAllocation;
import com.example.pointssubject.service.command.dto.UsePointResult;
import java.time.LocalDateTime;
import java.util.List;

public record UsePointResponse(
    Long useId,
    Long userId,
    String orderNumber,
    Long amount,
    LocalDateTime usedAt,
    List<UseAllocation> allocations
) {

    public static UsePointResponse from(UsePointResult result) {
        return new UsePointResponse(
            result.useId(),
            result.userId(),
            result.orderNumber(),
            result.amount(),
            result.usedAt(),
            result.allocations()
        );
    }
}
