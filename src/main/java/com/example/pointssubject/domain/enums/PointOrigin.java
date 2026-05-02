package com.example.pointssubject.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointOrigin {

    NORMAL("일반 적립"),
    USE_CANCEL_REISSUE("만료된 사용취소로 인한 재적립");

    private final String description;
}
