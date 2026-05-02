package com.example.pointssubject.exception;

import lombok.Getter;

/**
 * 도메인 예외 단일 진입점. 새 도메인 에러는 {@link PointErrorCode} enum 한 줄 추가로 정의되며,
 * 별도 specific 예외 클래스는 만들지 않는다 (architecture.md §V).
 */
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
