package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.CancelEarnCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CancelEarnRequest(
    @NotNull @Positive Long userId
) {

    public CancelEarnCommand toCommand(Long earnId) {
        return new CancelEarnCommand(userId, earnId);
    }
}
