package com.example.pointssubject.service.command.dto;

/**
 * 일반·수기 적립 모두 동일 입력. {@code source} 는 클라이언트 입력이 아니라 서비스 메서드 진입점으로 결정된다
 * ({@code earn(...)} → SYSTEM, {@code earnManual(...)} → MANUAL).
 */
public record EarnPointCommand(
    Long userId,
    Long amount,
    Integer expiryDays
) {
}
