package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceRequest;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceResponse;
import com.example.pointssubject.service.command.PointUserCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증/인가 미도입 — 운영 배포 시 Spring Security 또는 reverse proxy 인증 게이트로 보호 필수. */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final PointUserCommandService pointUserService;

    @PutMapping("/{userId}/max-balance")
    public UpdateUserMaxBalanceResponse updateMaxBalance(
        @PathVariable Long userId,
        @RequestBody @Valid UpdateUserMaxBalanceRequest request
    ) {
        var result = pointUserService.updateMaxBalance(request.toCommand(userId));
        return UpdateUserMaxBalanceResponse.from(result);
    }
}
