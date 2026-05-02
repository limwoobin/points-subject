package com.example.pointssubject.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EarnStatus {

    ACTIVE("사용 가능"),
    CANCELLED("적립 취소됨");

    private final String description;
}
