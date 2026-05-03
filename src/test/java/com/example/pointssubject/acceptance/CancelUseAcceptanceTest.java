package com.example.pointssubject.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.controller.dto.CancelUsePointResponse;
import com.example.pointssubject.controller.dto.UsePointResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelUseAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("사용 후 부분 환불을 요청하면 살아있는 원본 적립의 remaining 이 환불 금액만큼 복원되고 use 행은 부분 취소 상태가 된다")
    void 부분_사용취소_복원_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);
        UsePointResponse use = 사용이_요청됨(USER_ID, "ORD-PART", 600L);

        CancelUsePointResponse cancel = 사용취소가_요청됨(USER_ID, "ORD-PART", "ORF-PART", 200L);

        assertThat(cancel.amount()).isEqualTo(200L);
        assertThat(cancel.remainingCancellable()).isEqualTo(400L);
        // 사용 1000 - 600 + 환불 200 = 600
        적립_잔여가_확인됨(earnId, 600L);
        사용_부분_취소가_확인됨(use.useId());
        사용_잔여_환불가능액이_확인됨(use.useId(), 400L);
    }

    @Test
    @DisplayName("사용 후 원본 적립이 만료된 시점에 환불을 요청하면 환불 금액만큼 새 적립이 USE_CANCEL_REISSUE 로 발급된다")
    void 만료된_적립_재발급_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);
        사용이_요청됨(USER_ID, "ORD-EXP", 1000L);

        적립이_만료됨(earnId);

        CancelUsePointResponse cancel = 사용취소가_요청됨(USER_ID, "ORD-EXP", "ORF-EXP", 300L);

        var reissued = 재발급된_적립이_확인됨(cancel.cancelId(), 300L);
        assertThat(reissued.getId()).isNotEqualTo(earnId);
    }
}
