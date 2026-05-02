package com.example.pointssubject.controller.dto;

import com.example.pointssubject.domain.enums.PointSource;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import jakarta.validation.constraints.NotNull;

/**
 * 금액/만료일 범위 검증은 Service 단에서 FF4j 정책 값으로 수행 — annotation 상수와 중복하면 SoT 가 갈라짐.
 * Bean Validation 단계에는 NotNull 만 둔다.
 */
public record EarnPointRequest(
    @NotNull Long userId,
    @NotNull Long amount,
    PointSource source,
    Integer expiryDays
) {

    public EarnPointCommand toCommand() {
        return new EarnPointCommand(userId, amount, source, expiryDays);
    }
}
