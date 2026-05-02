package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.ErrorResponse;
import com.example.pointssubject.controller.dto.ErrorResponse.FieldError;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PointException.class)
    public ResponseEntity<ErrorResponse> handleDomain(PointException e) {
        PointErrorCode ec = e.getErrorCode();
        log.info("PointException: code={}, message={}", ec.getCode(), e.getMessage());
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
            .toList();
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), "요청 본문 형식이 올바르지 않습니다"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldError> fieldErrors = e.getConstraintViolations().stream()
            .map(cv -> {
                String path = cv.getPropertyPath().toString();
                String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                return new FieldError(field, cv.getMessage());
            })
            .toList();
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), fieldErrors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        List<FieldError> fieldErrors = List.of(
            new FieldError(e.getParameterName(), "필수 파라미터가 누락되었습니다")
        );
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        String required = e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "유효한 타입";
        List<FieldError> fieldErrors = List.of(
            new FieldError(e.getName(), required + " 타입이어야 합니다")
        );
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage(), fieldErrors));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), "지원하지 않는 HTTP 메서드입니다: " + e.getMethod()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException e) {
        PointErrorCode ec = PointErrorCode.VALIDATION_FAILED;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), "요청 경로를 찾을 수 없습니다: " + e.getRequestURL()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception e) {
        log.error("Unhandled exception", e);
        PointErrorCode ec = PointErrorCode.INTERNAL_ERROR;
        return ResponseEntity
            .status(ec.getHttpStatus())
            .body(ErrorResponse.of(ec.getCode(), ec.getMessage()));
    }
}
