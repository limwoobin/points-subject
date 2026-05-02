package com.example.pointssubject.service.command.dto;

/** 한 PointUse 가 어느 PointEarn 으로부터 얼마를 차감했는지의 단일 매핑. */
public record UseAllocation(
    Long earnId,
    Long amount
) {
}
