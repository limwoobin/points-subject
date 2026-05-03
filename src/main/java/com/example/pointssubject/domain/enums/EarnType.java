package com.example.pointssubject.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 적립 발생 분류 — 사용 우선순위(MANUAL 우선)와 reissue 식별용. */
@Getter
@RequiredArgsConstructor
public enum EarnType {

    SYSTEM("시스템 자동 적립"),
    MANUAL("관리자 수기 지급"),
    USE_CANCEL_REISSUE("만료된 사용취소로 인한 재적립");

    private final String description;
}
