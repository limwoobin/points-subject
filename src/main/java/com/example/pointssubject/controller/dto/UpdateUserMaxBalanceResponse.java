package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceResult;

public record UpdateUserMaxBalanceResponse(
    Long userId,
    Long maxBalance
) {

    public static UpdateUserMaxBalanceResponse from(UpdateUserMaxBalanceResult result) {
        return new UpdateUserMaxBalanceResponse(result.userId(), result.maxBalance());
    }
}
