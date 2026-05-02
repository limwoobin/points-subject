package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.UsePointCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UsePointRequest(
    @NotNull Long userId,
    @NotBlank @Size(max = 64) String orderNumber,
    @NotNull @Positive Long amount
) {

    public UsePointCommand toCommand() {
        return new UsePointCommand(userId, orderNumber, amount);
    }
}
