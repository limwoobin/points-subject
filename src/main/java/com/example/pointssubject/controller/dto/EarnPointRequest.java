package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.EarnPointCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 금액/만료일 범위 검증은 Service 단 (FF4j 정책 값). */
public record EarnPointRequest(
    @NotNull @Positive Long userId,
    @NotNull @Positive Long amount,
    Integer expiryDays
) {

    public EarnPointCommand toCommand() {
        return new EarnPointCommand(userId, amount, expiryDays);
    }
}
