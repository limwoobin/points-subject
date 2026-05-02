package com.example.pointssubject.policy;

import lombok.RequiredArgsConstructor;
import org.ff4j.FF4j;
import org.springframework.stereotype.Service;

/** 도메인 정책 값 단일 진입점. 도메인 코드는 {@link FF4j} 직접 참조 금지. */
@Service
@RequiredArgsConstructor
public class PointPolicyService {

    private final FF4j ff4j;

    public long earnMin()              { return readLong(PolicyKey.EARN_MIN_PER_TXN); }
    public long earnMax()              { return readLong(PolicyKey.EARN_MAX_PER_TXN); }
    public long balanceMaxPerUser()    { return readLong(PolicyKey.BALANCE_MAX_PER_USER); }
    public int  expiryDefaultDays()    { return (int) readLong(PolicyKey.EXPIRY_DEFAULT_DAYS); }
    public int  expiryMinDays()        { return (int) readLong(PolicyKey.EXPIRY_MIN_DAYS); }
    public int  expiryMaxDays()        { return (int) readLong(PolicyKey.EXPIRY_MAX_DAYS); }
    public int  useCancelReissueDays() { return (int) readLong(PolicyKey.USE_CANCEL_REISSUE_DAYS); }

    private long readLong(PolicyKey k) {
        return Long.parseLong(ff4j.getPropertyAsString(k.getKey()));
    }
}
