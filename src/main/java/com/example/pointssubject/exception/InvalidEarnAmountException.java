package com.example.pointssubject.exception;

import lombok.Getter;

@Getter
public class InvalidEarnAmountException extends PointException {

    private final long amount;
    private final long min;
    private final long max;

    public InvalidEarnAmountException(long amount, long min, long max) {
        super(PointErrorCode.EARN_AMOUNT_OUT_OF_RANGE,
            "amount=" + amount + ", allowed=" + min + "~" + max);
        this.amount = amount;
        this.min = min;
        this.max = max;
    }
}
