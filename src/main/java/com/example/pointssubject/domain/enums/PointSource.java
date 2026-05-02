package com.example.pointssubject.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointSource {

    SYSTEM("시스템 자동 적립"),
    MANUAL("관리자 수기 지급");

    private final String description;
}
