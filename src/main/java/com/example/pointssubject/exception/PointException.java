package com.example.pointssubject.exception;

import lombok.Getter;

@Getter
public abstract class PointException extends RuntimeException {

    private final PointErrorCode errorCode;

    protected PointException(PointErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    protected PointException(PointErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " (" + detail + ")");
        this.errorCode = errorCode;
    }
}
