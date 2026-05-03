package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.BalanceResponse;
import com.example.pointssubject.controller.dto.PointHistoryResponse;
import com.example.pointssubject.service.query.PointQueryService;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Validated
public class PointQueryController {

    private final PointQueryService queryService;

    @GetMapping("/users/{userId}/balance")
    public BalanceResponse getBalance(@PathVariable @Positive Long userId) {
        return BalanceResponse.from(queryService.getBalance(userId));
    }

    @GetMapping("/users/{userId}/history")
    public PointHistoryResponse getHistory(@PathVariable @Positive Long userId,
                                           @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                           @RequestParam(defaultValue = "10") @Positive int size) {
        return PointHistoryResponse.from(queryService.getHistory(userId, page, size));
    }
}
