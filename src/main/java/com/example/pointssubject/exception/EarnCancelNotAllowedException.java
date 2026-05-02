package com.example.pointssubject.exception;

import com.example.pointssubject.domain.enums.EarnStatus;
import lombok.Getter;

/** 취소 가능 조건: {@code status = ACTIVE AND remaining_amount = initial_amount}. */
@Getter
public class EarnCancelNotAllowedException extends PointException {

    private final Long earnId;
    private final EarnStatus status;
    private final long remainingAmount;
    private final long initialAmount;

    public EarnCancelNotAllowedException(Long earnId,
                                         EarnStatus status,
                                         long remainingAmount,
                                         long initialAmount) {
        super(PointErrorCode.EARN_CANCEL_NOT_ALLOWED,
            "earnId=" + earnId
                + ", status=" + status
                + ", remaining=" + remainingAmount
                + ", initial=" + initialAmount);
        this.earnId = earnId;
        this.status = status;
        this.remainingAmount = remainingAmount;
        this.initialAmount = initialAmount;
    }
}
