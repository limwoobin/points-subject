package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.service.command.PointEarnCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 전용 포인트 변경 진입점. 일반 클라이언트 경로({@link PointCommandController})와 분리해
 * MANUAL/SYSTEM 식별 무결성을 보호한다 — 일반 사용자가 자기 적립을 수기 적립으로 둔갑시키는 시나리오 차단.
 *
 * <p>인증/인가 미도입 — 운영 배포 시 Spring Security 또는 reverse proxy 인증 게이트로 보호 필수.
 */
@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
public class AdminPointController {

    private final PointEarnCommandService earnService;

    /** 운영자 수기 적립. 저장된 row 의 {@code source = MANUAL}. */
    @PostMapping("/earn")
    public ResponseEntity<EarnPointResponse> earnManual(@RequestBody @Valid EarnPointRequest request) {
        var result = earnService.earnManual(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(EarnPointResponse.from(result));
    }
}
