package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceRequest;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceResponse;
import com.example.pointssubject.service.command.PointEarnCommandService;
import com.example.pointssubject.service.command.PointUserCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 전용 — MANUAL source 적립 / 회원 한도 변경 권한.
 * <p>
 * 본 과제는 UI 가 없는 API-only 제출물이라 인증 게이트는 의도적으로 미적용.
 * 외부 노출 시 다음 중 하나 이상으로 보호 필수:
 * <ul>
 *   <li>Spring Security + HTTP Basic / OAuth2 (사내 IdP 연동)</li>
 *   <li>{@code X-Admin-Token} 헤더 + 정적 토큰 필터 (간이 환경)</li>
 *   <li>API Gateway / Reverse Proxy 레이어에서 path 기반 인증 강제</li>
 * </ul>
 * 추가로 audit log (현재 {@code point_action_log}) 에 호출자 식별자 (operator id) 기록 권장.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class AdminPointController {

    private final PointEarnCommandService earnService;
    private final PointUserCommandService pointUserService;

    @PostMapping("/points/earn")
    public ResponseEntity<EarnPointResponse> earnManual(@RequestBody @Valid EarnPointRequest request) {
        var result = earnService.earnManual(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(EarnPointResponse.from(result));
    }

    @PutMapping("/users/{userId}/max-balance")
    public UpdateUserMaxBalanceResponse updateMaxBalance(@PathVariable @Positive Long userId,
                                                         @RequestBody @Valid UpdateUserMaxBalanceRequest request) {
        var result = pointUserService.updateMaxBalance(request.toCommand(userId));
        return UpdateUserMaxBalanceResponse.from(result);
    }
}
