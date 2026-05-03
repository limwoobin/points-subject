package com.example.pointssubject.service.command.dto;

public record CancelUsePointCommand(
    Long userId,
    String orderNumber,
    String orderRefundId,
    Long amount
) {
}
