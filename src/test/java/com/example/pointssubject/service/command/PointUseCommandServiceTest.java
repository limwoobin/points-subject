package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUseRepository;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import com.example.pointssubject.service.command.dto.UsePointCommand;
import com.example.pointssubject.service.command.dto.UsePointResult;
import com.example.pointssubject.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PointUseCommandServiceTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointEarnCommandService earnService;
    @Autowired private PointUseCommandService useService;
    @Autowired private PointEarnRepository earnRepository;
    @Autowired private PointUseRepository useRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("기본 사용 흐름")
    class BasicUse {

        @Test
        @DisplayName("적립 후 일부 사용을 호출하면 사용된 적립의 remaining_amount 가 차감되고 잔액 합계도 그만큼 줄어든다")
        void use_decrements_remaining_amount_and_balance() {
            EarnPointResult earned = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));

            UsePointResult result = useService.use(
                new UsePointCommand(USER_ID, "ORD-1", 600L));

            assertThat(result.amount()).isEqualTo(600L);
            assertThat(result.allocations()).hasSize(1);
            assertThat(result.allocations().get(0).earnId()).isEqualTo(earned.earnId());
            assertThat(result.allocations().get(0).amount()).isEqualTo(600L);

            entityManager.flush();
            entityManager.clear();
            PointEarn refreshed = earnRepository.findById(earned.earnId()).orElseThrow();
            assertThat(refreshed.getRemainingAmount()).isEqualTo(400L);
            assertThat(earnRepository.sumActiveBalance(USER_ID, LocalDateTime.now())).isEqualTo(400L);
        }

        @Test
        @DisplayName("A(1000) + B(500) 적립 후 1200 사용 시 A 에서 1000, B 에서 200 이 차감된다")
        void use_1200_consumes_A_fully_then_B() {
            EarnPointResult a = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            EarnPointResult b = earnService.earn(new EarnPointCommand(USER_ID, 500L, null));

            UsePointResult result = useService.use(
                new UsePointCommand(USER_ID, "A1234", 1200L));

            assertThat(result.allocations()).hasSize(2);
            assertThat(result.allocations().get(0).earnId()).isEqualTo(a.earnId());
            assertThat(result.allocations().get(0).amount()).isEqualTo(1000L);
            assertThat(result.allocations().get(1).earnId()).isEqualTo(b.earnId());
            assertThat(result.allocations().get(1).amount()).isEqualTo(200L);

            entityManager.flush();
            entityManager.clear();
            assertThat(earnRepository.findById(a.earnId()).orElseThrow().getRemainingAmount()).isZero();
            assertThat(earnRepository.findById(b.earnId()).orElseThrow().getRemainingAmount()).isEqualTo(300L);
        }
    }

    // 우선순위 (수기 우선, 만료 임박 순) happy path 는 UseAcceptanceTest 가 HTTP layer 에서 검증하고,
    // 정렬 규칙 격리는 PointEarnUsePriorityTest 가 Comparator 단위 테스트로 검증한다 — service-level 중복 제거.

    @Nested
    @DisplayName("멱등성 — orderNumber 중복")
    class OrderNumberIdempotency {

        @Test
        @DisplayName("같은 orderNumber 로 두 번 사용을 호출하면 두 번째는 USE_ORDER_NUMBER_DUPLICATED 로 거부되고 PointUse row 는 1건만 남는다")
        void duplicate_order_number_creates_only_one_use() {
            earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-DUP", 100L));

            assertThatThrownBy(() -> useService.use(new UsePointCommand(USER_ID, "ORD-DUP", 100L)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.USE_ORDER_NUMBER_DUPLICATED);

            assertThat(useRepository.findByOrderNumber("ORD-DUP")).hasSize(1);
        }
    }
}
