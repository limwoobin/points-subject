package com.example.pointssubject.service.command.dto;

/** source 는 진입점이 결정 (earn=SYSTEM, earnManual=MANUAL) — 외부 입력 아님. */
public record EarnPointCommand(
    Long userId,
    Long amount,
    Integer expiryDays
) {
}
