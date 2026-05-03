package com.example.pointssubject.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** point_use 행 종류 — USE 와 USE_CANCEL 을 단일 테이블에 공존 (sparse-column inheritance). */
@Getter
@RequiredArgsConstructor
public enum PointUseType {

    USE("사용"),
    USE_CANCEL("사용취소");

    private final String description;
}
