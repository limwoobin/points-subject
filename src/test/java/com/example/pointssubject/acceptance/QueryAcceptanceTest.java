package com.example.pointssubject.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.controller.dto.BalanceResponse;
import com.example.pointssubject.controller.dto.PointHistoryResponse;
import com.example.pointssubject.domain.enums.PointActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("적립 후 잔액을 조회하면 ACTIVE + 미만료 적립의 remaining 합계가 응답된다")
    void 잔액_조회_시나리오() throws Exception {
        적립이_요청됨(USER_ID, 1000L);
        적립이_요청됨(USER_ID, 500L);

        BalanceResponse balance = 잔액_조회됨(USER_ID);

        assertThat(balance.availableBalance()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("적립취소된 적립과 만료된 적립은 잔액 합계에서 제외된다")
    void 잔액_조회는_취소_및_만료_적립을_제외_시나리오() throws Exception {
        적립이_요청됨(USER_ID, 1000L);
        Long cancelledId = 적립이_요청됨(USER_ID, 500L);
        Long expiredId = 적립이_요청됨(USER_ID, 300L);
        적립취소가_요청됨(USER_ID, cancelledId);
        적립이_만료됨(expiredId);

        BalanceResponse balance = 잔액_조회됨(USER_ID);

        // 살아있는 1000 만 합산
        assertThat(balance.availableBalance()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("회원 이력을 조회하면 적립/적립취소/사용/사용취소 가 발생 시각 내림차순으로 통합되어 응답된다")
    void 이력_조회_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);
        사용이_요청됨(USER_ID, "ORD-HIST", 600L);
        사용취소가_요청됨(USER_ID, "ORD-HIST", "ORF-HIST", 200L);

        PointHistoryResponse history = 이력_조회됨(USER_ID);

        // 이벤트 3건: EARN(1000) + USE(600) + USE_CANCEL(200)
        assertThat(history.items()).hasSize(3);
        assertThat(history.totalElements()).isEqualTo(3);
        assertThat(history.page()).isZero();
        assertThat(history.size()).isEqualTo(10);
        assertThat(history.hasNext()).isFalse();

        // 발생 역순 — 가장 최근(USE_CANCEL) 가 첫 항목
        assertThat(history.items().get(0).type()).isEqualTo(PointActionType.USE_CANCEL);
        assertThat(history.items().get(0).amount()).isEqualTo(200L);
        assertThat(history.items().get(0).orderNumber()).isEqualTo("ORD-HIST");

        assertThat(history.items().get(1).type()).isEqualTo(PointActionType.USE);
        assertThat(history.items().get(1).amount()).isEqualTo(600L);
        assertThat(history.items().get(1).orderNumber()).isEqualTo("ORD-HIST");

        assertThat(history.items().get(2).type()).isEqualTo(PointActionType.EARN);
        assertThat(history.items().get(2).id()).isEqualTo(earnId);
        assertThat(history.items().get(2).amount()).isEqualTo(1000L);
        assertThat(history.items().get(2).orderNumber()).isNull();
    }

    @Test
    @DisplayName("적립 후 취소된 항목은 EARN + EARN_CANCEL 두 시점으로 펼쳐 노출된다")
    void 이력_조회는_적립과_적립취소를_분리_시나리오() throws Exception {
        Long earnId = 적립이_요청됨(USER_ID, 1000L);
        적립취소가_요청됨(USER_ID, earnId);

        PointHistoryResponse history = 이력_조회됨(USER_ID);

        assertThat(history.items()).hasSize(2);
        assertThat(history.items()).extracting(PointHistoryResponse.Item::type)
            .containsExactlyInAnyOrder(PointActionType.EARN, PointActionType.EARN_CANCEL);
        assertThat(history.items()).allSatisfy(item -> {
            assertThat(item.id()).isEqualTo(earnId);
            assertThat(item.amount()).isEqualTo(1000L);
        });
    }

    @Test
    @DisplayName("페이징 파라미터를 지정하지 않으면 size=10 default 가 적용되고 11번째 이후는 잘려서 hasNext=true 가 된다")
    void 이력_조회_페이징_default_size() throws Exception {
        // 12건 적립 → 액션 로그 12행
        for (int i = 0; i < 12; i++) {
            적립이_요청됨(USER_ID, 100L);
        }

        PointHistoryResponse first = 이력_조회됨(USER_ID);

        assertThat(first.size()).isEqualTo(10);
        assertThat(first.items()).hasSize(10);
        assertThat(first.totalElements()).isEqualTo(12);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    @DisplayName("size 파라미터로 페이지 크기를 조정하고 page 파라미터로 다음 페이지 항목을 조회할 수 있다")
    void 이력_조회_페이징_size_page_파라미터() throws Exception {
        for (int i = 0; i < 5; i++) {
            적립이_요청됨(USER_ID, 100L);
        }

        PointHistoryResponse page0 = 이력_조회됨(USER_ID, 0, 2);
        assertThat(page0.items()).hasSize(2);
        assertThat(page0.page()).isZero();
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.totalElements()).isEqualTo(5);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.hasNext()).isTrue();

        PointHistoryResponse page2 = 이력_조회됨(USER_ID, 2, 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.hasNext()).isFalse();
    }

}
