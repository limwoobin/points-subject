package com.example.pointssubject.service.command.dto;

public record UsePointCommand(
    Long userId,
    String orderNumber,
    Long amount
) {
}
