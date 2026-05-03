package com.example.pointssubject.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** point_action_log 영속 컬럼 + 이력 조회 응답 타입 겸용 (view 전용 enum 미분리). */
@Getter
@RequiredArgsConstructor
public enum PointActionType {

    EARN("적립"),
    EARN_CANCEL("적립취소"),
    USE("사용"),
    USE_CANCEL("사용취소");

    private final String description;
}
