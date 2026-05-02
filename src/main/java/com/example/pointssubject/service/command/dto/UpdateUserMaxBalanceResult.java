package com.example.pointssubject.service.command.dto;

import com.example.pointssubject.domain.entity.PointUser;

public record UpdateUserMaxBalanceResult(
    Long userId,
    Long maxBalance
) {

    public static UpdateUserMaxBalanceResult from(PointUser user) {
        return new UpdateUserMaxBalanceResult(user.getUserId(), user.getMaxBalance());
    }
}
