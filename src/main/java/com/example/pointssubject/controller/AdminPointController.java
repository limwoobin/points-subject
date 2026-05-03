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
