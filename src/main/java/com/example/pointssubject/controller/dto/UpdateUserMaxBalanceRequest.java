package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceCommand;
import jakarta.validation.constraints.PositiveOrZero;

/** maxBalance: {@code null} 이면 override 해제, {@code 0} 은 적립 차단 의도로 명시적 허용. */
public record UpdateUserMaxBalanceRequest(
    @PositiveOrZero Long maxBalance
) {

    public UpdateUserMaxBalanceCommand toCommand(Long userId) {
        return new UpdateUserMaxBalanceCommand(userId, maxBalance);
    }
}
