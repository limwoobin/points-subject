package com.example.pointssubject.domain;

/** PointUseDetail 의 in-flight 형태 — 분배 알고리즘 결과이자 service result 노출용. */
public record UseAllocation(
    Long earnId,
    Long amount
) {
}
