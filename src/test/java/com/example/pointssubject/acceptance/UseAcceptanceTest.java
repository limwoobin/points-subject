package com.example.pointssubject.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.controller.dto.UsePointResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UseAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("적립 후 주문번호와 함께 사용을 요청하면 적립의 remaining 이 차감되고 사용 row 가 주문번호로 식별 가능해진다")
    void 단일_적립_사용_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);

        UsePointResponse use = 사용이_요청됨(USER_ID, "ORD-USE-1", 600L);

        assertThat(use.orderNumber()).isEqualTo("ORD-USE-1");
        assertThat(use.allocations()).hasSize(1);
        assertThat(use.allocations().get(0).earnId()).isEqualTo(earnId);
        assertThat(use.allocations().get(0).amount()).isEqualTo(600L);
        적립_잔여가_확인됨(earnId, 400L);
        잔액이_확인됨(USER_ID, 400L);
    }

    @Test
    @DisplayName("수기 적립과 시스템 적립이 공존할 때 사용은 수기 적립부터 차감된다")
    void 수기_우선_사용_시나리오() throws Exception {
        Long systemEarnId = 적립이_요청됨(USER_ID, 1000L);
        Long manualEarnId = 수기_적립이_요청됨(USER_ID, 500L);

        UsePointResponse use = 사용이_요청됨(USER_ID, "ORD-MANUAL-FIRST", 700L);

        // MANUAL 500 전부 → SYSTEM 200
        assertThat(use.allocations()).hasSize(2);
        assertThat(use.allocations().get(0).earnId()).isEqualTo(manualEarnId);
        assertThat(use.allocations().get(0).amount()).isEqualTo(500L);
        assertThat(use.allocations().get(1).earnId()).isEqualTo(systemEarnId);
        assertThat(use.allocations().get(1).amount()).isEqualTo(200L);
        적립_잔여가_확인됨(manualEarnId, 0L);
        적립_잔여가_확인됨(systemEarnId, 800L);
        잔액이_확인됨(USER_ID, 800L);
    }

    @Test
    @DisplayName("동일 source 의 적립이 여러 개일 때 만료일이 더 가까운 적립부터 사용된다 (PRD §3.3.3)")
    void 만료_임박_순_사용_시나리오() throws Exception {
        // 같은 SYSTEM source, 만료일만 다름
        Long lateId = 적립이_요청됨(USER_ID, 500L, 100);
        Long earlyId = 적립이_요청됨(USER_ID, 500L, 30);

        UsePointResponse use = 사용이_요청됨(USER_ID, "ORD-EXPIRY-ORDER", 400L);

        // early 만 사용되고 late 는 손대지 않음
        assertThat(use.allocations()).hasSize(1);
        assertThat(use.allocations().get(0).earnId()).isEqualTo(earlyId);
        assertThat(use.allocations().get(0).amount()).isEqualTo(400L);
        적립_잔여가_확인됨(earlyId, 100L);
        적립_잔여가_확인됨(lateId, 500L);
    }
}
