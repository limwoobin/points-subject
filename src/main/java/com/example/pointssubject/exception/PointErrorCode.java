package com.example.pointssubject.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 코드 prefix: 0xx 공통 / 1xx 적립 / 2xx 적립취소 / 3xx 사용 / 4xx 사용취소. */
@Getter
@RequiredArgsConstructor
public enum PointErrorCode {

    // 공통 (POINT-0xx)
    VALIDATION_FAILED("POINT-001", "요청 값 검증에 실패했습니다", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR("POINT-099", "서버 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // 적립 (POINT-1xx)
    EARN_AMOUNT_OUT_OF_RANGE("POINT-101", "1회 적립 금액이 허용 범위를 벗어났습니다", HttpStatus.BAD_REQUEST),
    EARN_EXPIRY_OUT_OF_RANGE("POINT-102", "만료일이 허용 범위를 벗어났습니다", HttpStatus.BAD_REQUEST),
    EARN_BALANCE_LIMIT_EXCEEDED("POINT-103", "회원 보유 한도를 초과합니다", HttpStatus.CONFLICT),

    // 적립취소 (POINT-2xx)
    EARN_NOT_FOUND("POINT-201", "적립 건을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    EARN_CANCEL_NOT_ALLOWED("POINT-202", "이미 사용되었거나 취소된 적립은 취소할 수 없습니다", HttpStatus.CONFLICT),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
