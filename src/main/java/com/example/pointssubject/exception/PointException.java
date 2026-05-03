package com.example.pointssubject.exception;

import lombok.Getter;

/** 도메인 예외 단일 진입점 — specific 예외 클래스는 만들지 않고 PointErrorCode enum 으로 분기. */
@Getter
public class PointException extends RuntimeException {

    private final PointErrorCode errorCode;

    public PointException(PointErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PointException(PointErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " (" + detail + ")");
        this.errorCode = errorCode;
    }
}
