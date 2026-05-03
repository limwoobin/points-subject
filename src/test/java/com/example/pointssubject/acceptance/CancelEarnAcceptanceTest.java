package com.example.pointssubject.acceptance;

import com.example.pointssubject.domain.enums.EarnStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEarnAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("적립 후 사용 전이라면 적립취소가 가능하며 status=CANCELLED 가 되고 잔액 합계에서 제외된다")
    void 미사용_적립_취소_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);
        잔액이_확인됨(USER_ID, 1000L);

        적립취소가_요청됨(USER_ID, earnId);

        적립_상태가_확인됨(earnId, EarnStatus.CANCELLED);
        잔액이_확인됨(USER_ID, 0L);
    }
}
