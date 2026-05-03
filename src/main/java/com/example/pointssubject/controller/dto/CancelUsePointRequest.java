package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.CancelUsePointCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CancelUsePointRequest(
    @NotNull @Positive Long userId,
    @NotBlank @Size(max = 64) String orderNumber,
    @NotBlank @Size(max = 64) String orderRefundId,
    @NotNull @Positive Long amount
) {

    public CancelUsePointCommand toCommand() {
        return new CancelUsePointCommand(userId, orderNumber, orderRefundId, amount);
    }
}
