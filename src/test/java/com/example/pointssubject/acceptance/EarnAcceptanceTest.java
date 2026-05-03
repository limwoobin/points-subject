package com.example.pointssubject.acceptance;

import com.example.pointssubject.domain.enums.EarnType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EarnAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("회원이 1000 포인트 적립을 요청하면 잔액이 1000 이 되고 적립 row 는 source=SYSTEM 으로 저장된다")
    void 일반_적립_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);

        잔액이_확인됨(USER_ID, 1000L);
        적립_잔여가_확인됨(earnId, 1000L);
        적립_type이_확인됨(earnId, EarnType.SYSTEM);
    }

    @Test
    @DisplayName("관리자가 수기 적립을 지급하면 source=MANUAL 로 저장되어 일반 적립과 구분 식별된다")
    void 수기_적립_식별_시나리오() throws Exception {
        Long earnId = 수기_적립이_요청됨(USER_ID, 500L);

        잔액이_확인됨(USER_ID, 500L);
        적립_type이_확인됨(earnId, EarnType.MANUAL);
    }
}
