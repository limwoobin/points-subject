package com.example.pointssubject.service.command.dto;

public record UpdateUserMaxBalanceCommand(
    Long userId,
    Long maxBalance
) {
}
