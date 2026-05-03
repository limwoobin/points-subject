package com.example.pointssubject.controller.dto;

import com.example.pointssubject.service.query.dto.BalanceView;

public record BalanceResponse(
    Long userId,
    Long availableBalance
) {

    public static BalanceResponse from(BalanceView view) {
        return new BalanceResponse(view.userId(), view.availableBalance());
    }
}
