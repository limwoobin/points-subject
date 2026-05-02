package com.example.pointssubject.exception;

import lombok.Getter;

@Getter
public class EarnNotFoundException extends PointException {

    private final Long earnId;

    public EarnNotFoundException(Long earnId) {
        super(PointErrorCode.EARN_NOT_FOUND, "earnId=" + earnId);
        this.earnId = earnId;
    }
}
