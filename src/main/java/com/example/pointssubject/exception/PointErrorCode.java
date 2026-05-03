package com.example.pointssubject.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

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

    // 사용 (POINT-3xx) — amount/orderNumber 형식 검증은 controller @Valid 단계에서 처리되어 POINT-001 로 매핑됨
    USE_INSUFFICIENT_BALANCE("POINT-301", "보유 잔액이 사용 금액보다 부족합니다", HttpStatus.CONFLICT),
    USE_ORDER_NUMBER_DUPLICATED("POINT-302", "이미 처리된 주문번호입니다", HttpStatus.CONFLICT),

    // 사용취소 (POINT-4xx)
    USE_NOT_FOUND("POINT-401", "사용 건을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    USE_CANCEL_AMOUNT_EXCEEDED("POINT-402", "취소 가능 금액을 초과합니다", HttpStatus.CONFLICT),
    ORDER_REFUND_ID_DUPLICATED("POINT-403", "이미 처리된 환불 요청입니다", HttpStatus.CONFLICT),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
