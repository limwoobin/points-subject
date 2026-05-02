package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.entity.PointUse;
import java.time.LocalDateTime;
import java.util.List;

public record UsePointResult(
    Long useId,
    Long userId,
    String orderNumber,
    Long amount,
    LocalDateTime usedAt,
    List<UseAllocation> allocations
) {

    public static UsePointResult from(PointUse use, List<UseAllocation> allocations) {
        return new UsePointResult(
            use.getId(),
            use.getUserId(),
            use.getOrderNumber(),
            use.getAmount(),
            use.getCreatedAt(),
            allocations
        );
    }
}
