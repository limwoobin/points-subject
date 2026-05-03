package com.example.pointssubject.policy;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** seed default 는 yml 이 단일 소스 (drift 방지). description 은 FF4j Console 노출용. */
@Getter
@RequiredArgsConstructor
public enum PolicyKey {

    EARN_MIN_PER_TXN(
        "points.earn.min-per-transaction",
        "1회 적립 하한 (1포인트 이상)"),

    EARN_MAX_PER_TXN(
        "points.earn.max-per-transaction",
        "1회 적립 한도 (10만포인트 이하)"),

    BALANCE_MAX_PER_USER(
        "points.balance.max-per-user",
        "회원별 보유 한도 글로벌 default — 회원별 override 는 point_user.max_balance"),

    EXPIRY_DEFAULT_DAYS(
        "points.expiry.default-days",
        "만료일 기본값 (365일)"),

    EXPIRY_MIN_DAYS(
        "points.expiry.min-days",
        "만료일 하한 (1일 이상, inclusive)"),

    EXPIRY_MAX_DAYS(
        "points.expiry.max-days",
        "만료일 상한 (5년 미만, exclusive)"),

    USE_CANCEL_REISSUE_DAYS(
        "points.use-cancel.reissue-days",
        "사용취소 시 만료된 적립을 신규 적립으로 재발급할 때의 만료일"),
    ;

    private final String key;
    private final String description;
}
