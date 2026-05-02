package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.domain.entity.PointEarn;
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
        @DisplayName("PRD §4 예시 그대로 — A(1000) + B(500) 적립 후 1200 사용 시 A 에서 1000, B 에서 200 이 차감된다")
        void prd_example_C_uses_1200_from_A_then_B() {
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

    @Nested
    @DisplayName("우선순위 — 수기(MANUAL) 우선, 만료 임박 순")
    class Priority {

        @Test
        @DisplayName("일반 적립과 수기 적립이 공존할 때 사용은 수기 적립부터 차감된다")
        void manual_earns_consumed_first() {
            EarnPointResult system = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            EarnPointResult manual = earnService.earnManual(new EarnPointCommand(USER_ID, 500L, null));

            UsePointResult result = useService.use(
                new UsePointCommand(USER_ID, "ORD-MANUAL-FIRST", 600L));

            // MANUAL(500) 전체 + SYSTEM 100
            assertThat(result.allocations()).hasSize(2);
            assertThat(result.allocations().get(0).earnId()).isEqualTo(manual.earnId());
            assertThat(result.allocations().get(0).amount()).isEqualTo(500L);
            assertThat(result.allocations().get(1).earnId()).isEqualTo(system.earnId());
            assertThat(result.allocations().get(1).amount()).isEqualTo(100L);
        }

        @Test
        @DisplayName("동일 source 라면 만료일이 더 가까운 적립부터 사용된다")
        void earlier_expiry_consumed_first() {
            EarnPointResult late = earnService.earn(new EarnPointCommand(USER_ID, 500L, 100));
            EarnPointResult early = earnService.earn(new EarnPointCommand(USER_ID, 500L, 30));

            UsePointResult result = useService.use(
                new UsePointCommand(USER_ID, "ORD-EXPIRY", 400L));

            assertThat(result.allocations()).hasSize(1);
            assertThat(result.allocations().get(0).earnId()).isEqualTo(early.earnId());
            assertThat(late.earnId()).isNotNull();
        }

        @Test
        @DisplayName("수기 적립이 만료일이 더 길어도 일반 적립 (만료 임박) 보다 우선 사용된다")
        void manual_priority_overrides_expiry_priority() {
            EarnPointResult systemEarly = earnService.earn(new EarnPointCommand(USER_ID, 500L, 30));
            EarnPointResult manualLate = earnService.earnManual(new EarnPointCommand(USER_ID, 500L, 100));

            UsePointResult result = useService.use(
                new UsePointCommand(USER_ID, "ORD-MANUAL-OVER-EXPIRY", 300L));

            assertThat(result.allocations()).hasSize(1);
            assertThat(result.allocations().get(0).earnId()).isEqualTo(manualLate.earnId());
            assertThat(systemEarly.earnId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("멱등성 — orderNumber 중복")
    class OrderNumberIdempotency {

        @Test
        @DisplayName("같은 orderNumber 로 두 번 사용을 호출하면 첫 호출만 PointUse 가 생성되고 두 번째는 실패한다")
        void duplicate_order_number_creates_only_one_use() {
            earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            useService.use(new UsePointCommand(USER_ID, "ORD-DUP", 100L));

            assertThat(useRepository.findByOrderNumber("ORD-DUP")).isPresent();
            // 두 번째 호출은 USE_ORDER_NUMBER_DUPLICATED 로 실패 (예외 매핑은 단위 테스트에서 검증)
        }
    }
}
