package com.example.pointssubject.exception;

import lombok.Getter;

/** 범위 의미: {@code minInclusive <= days < maxExclusive}. */
@Getter
public class InvalidExpiryDaysException extends PointException {

    private final int days;
    private final int minInclusive;
    private final int maxExclusive;

    public InvalidExpiryDaysException(int days, int minInclusive, int maxExclusive) {
        super(PointErrorCode.EARN_EXPIRY_OUT_OF_RANGE,
            "days=" + days + ", allowed=" + minInclusive + " <= days < " + maxExclusive);
        this.days = days;
        this.minInclusive = minInclusive;
        this.maxExclusive = maxExclusive;
    }
}
