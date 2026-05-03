package com.example.pointssubject.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.controller.dto.CancelUsePointResponse;
import com.example.pointssubject.controller.dto.UsePointResponse;
import com.example.pointssubject.domain.entity.PointEarn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 복합 시나리오 — 한 환불 호출에서 reissue + restore 동시 발생하는 경계 케이스. */
class PrdScenarioAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("A 1000 + B 500 적립 → 1200 사용 (A 1000 + B 200 분배) → A 만료 → 1100 부분환불 시 만료된 A 분 1000 만큼 E 신규적립 + B 100 복원 + C 잔여 환불 가능 100")
    void full_scenario_with_reissue_and_restore() throws Exception {
        // A 1000 + B 500 적립 — 잔액 0 → 1500
        Long aId = 적립이_요청됨(USER_ID, 1000L);
        Long bId = 적립이_요청됨(USER_ID, 500L);
        잔액이_확인됨(USER_ID, 1500L);

        // 주문 A1234 에서 1200 사용 — 잔액 1500 → 300
        UsePointResponse c = 사용이_요청됨(USER_ID, "A1234", 1200L);
        // A 에서 1000 차감
        assertThat(c.allocations().get(0).earnId()).isEqualTo(aId);
        assertThat(c.allocations().get(0).amount()).isEqualTo(1000L);
        // B 에서 200 차감
        assertThat(c.allocations().get(1).earnId()).isEqualTo(bId);
        assertThat(c.allocations().get(1).amount()).isEqualTo(200L);
        적립_잔여가_확인됨(aId, 0L);
        적립_잔여가_확인됨(bId, 300L);
        잔액이_확인됨(USER_ID, 300L);

        // A 적립이 만료됨
        적립이_만료됨(aId);

        // C 의 1100 부분 사용취소 — 잔액 300 → 1400
        CancelUsePointResponse d = 사용취소가_요청됨(USER_ID, "A1234", "ORF-D", 1100L);

        // 응답 검증: 환불액 + 잔여 환불 가능
        assertThat(d.amount()).isEqualTo(1100L);
        assertThat(d.remainingCancellable()).isEqualTo(100L);

        // A 만료 → 1000 만큼 E 신규적립 (origin=USE_CANCEL_REISSUE, originUseCancelId=D)
        PointEarn reissuedE = 재발급된_적립이_확인됨(d.cancelId(), 1000L);
        assertThat(reissuedE.getId()).isNotEqualTo(aId);

        // B 살아있음 → 300 → 400 복원
        적립_잔여가_확인됨(bId, 400L);

        // 잔액 = E(1000) + B(400) = 1400
        잔액이_확인됨(USER_ID, 1400L);

        // C 잔여 환불 가능 = 1200 - 1100 = 100, 부분 취소 상태
        사용_잔여_환불가능액이_확인됨(c.useId(), 100L);
        사용_부분_취소가_확인됨(c.useId());
    }
}
