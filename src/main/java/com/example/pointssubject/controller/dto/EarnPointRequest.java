package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.command.dto.EarnPointCommand;
import jakarta.validation.constraints.NotNull;

/**
 * 금액/만료일 범위 검증은 Service 단에서 FF4j 정책 값으로 수행 — annotation 상수와 중복하면 SoT 가 갈라진다.
 * Bean Validation 단계에는 NotNull 만 둔다.
 * <p>
 * {@code source} 는 클라이언트 입력으로 받지 않는다. 일반 적립 API 는 항상 {@code SYSTEM},
 * 수기 적립은 별도 admin 엔드포인트 (`POST /api/admin/points/earn`) 가 {@code MANUAL} 로 강제한다.
 */
public record EarnPointRequest(
    @NotNull Long userId,
    @NotNull Long amount,
    Integer expiryDays
) {

    public EarnPointCommand toCommand() {
        return new EarnPointCommand(userId, amount, expiryDays);
    }
}
