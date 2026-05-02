package com.example.pointssubject.exception;

import lombok.Getter;

@Getter
public class BalanceLimitExceededException extends PointException {

    private final long currentBalance;
    private final long requestedAmount;
    private final long limit;

    public BalanceLimitExceededException(long currentBalance, long requestedAmount, long limit) {
        super(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED,
            "currentBalance=" + currentBalance + ", requested=" + requestedAmount + ", limit=" + limit);
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
        this.limit = limit;
    }
}
