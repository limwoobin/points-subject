package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.enums.PointSource;

public record EarnPointCommand(
    Long userId,
    Long amount,
    PointSource source,
    Integer expiryDays
) {
}
