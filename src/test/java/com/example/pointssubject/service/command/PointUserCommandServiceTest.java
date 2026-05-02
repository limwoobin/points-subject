package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.policy.PointPolicyService;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceCommand;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceResult;
import com.example.pointssubject.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PointUserCommandServiceTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointUserCommandService pointUserService;
    @Autowired private PointEarnCommandService earnService;
    @Autowired private PointUserRepository userRepository;
    @Autowired private PointPolicyService policy;

    @Nested
    @DisplayName("회원 보유 한도 갱신")
    class Update {

        @Test
        @DisplayName("회원 row 가 없는 상태에서 한도를 변경하면 row 가 자동 생성되고 한도가 설정된다")
        void creates_user_row_when_absent() {
            assertThat(userRepository.findById(USER_ID)).isEmpty();

            UpdateUserMaxBalanceResult result = pointUserService.updateMaxBalance(
                new UpdateUserMaxBalanceCommand(USER_ID, 5000L));

            assertThat(result.maxBalance()).isEqualTo(5000L);
            PointUser saved = userRepository.findById(USER_ID).orElseThrow();
            assertThat(saved.getMaxBalance()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("기존 회원의 한도를 변경하면 DB 의 max_balance 가 새 값으로 갱신된다")
        void updates_existing_user_limit() {
            earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            assertThat(userRepository.findById(USER_ID).orElseThrow().getMaxBalance()).isNull();

            pointUserService.updateMaxBalance(new UpdateUserMaxBalanceCommand(USER_ID, 2000L));

            assertThat(userRepository.findById(USER_ID).orElseThrow().getMaxBalance()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("maxBalance=null 로 한도를 변경하면 override 가 해제되어 max_balance 컬럼이 NULL 이 된다")
        void resets_override_when_null() {
            pointUserService.updateMaxBalance(new UpdateUserMaxBalanceCommand(USER_ID, 5000L));
            assertThat(userRepository.findById(USER_ID).orElseThrow().getMaxBalance()).isEqualTo(5000L);

            pointUserService.updateMaxBalance(new UpdateUserMaxBalanceCommand(USER_ID, null));

            assertThat(userRepository.findById(USER_ID).orElseThrow().getMaxBalance()).isNull();
        }
    }

    @Nested
    @DisplayName("한도 변경의 적립 흐름 즉시 반영")
    class IntegrationWithEarn {

        @Test
        @DisplayName("기존 잔액보다 작게 한도를 낮추면 기존 잔액은 유지되고 이후 적립만 거부된다")
        void lowering_limit_blocks_future_earn_but_keeps_existing_balance() {
            earnService.earn(new EarnPointCommand(USER_ID, 5000L, null));

            pointUserService.updateMaxBalance(new UpdateUserMaxBalanceCommand(USER_ID, 3000L));

            assertThatThrownBy(() ->
                earnService.earn(new EarnPointCommand(USER_ID, 1L, null)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("override 를 해제하면 다음 적립부터 글로벌 default 한도가 즉시 적용된다")
        void resetting_override_falls_back_to_global_default() {
            pointUserService.updateMaxBalance(new UpdateUserMaxBalanceCommand(USER_ID, 100L));
            earnService.earn(new EarnPointCommand(USER_ID, 100L, null));
            assertThatThrownBy(() ->
                earnService.earn(new EarnPointCommand(USER_ID, 1L, null)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED);

            pointUserService.updateMaxBalance(new UpdateUserMaxBalanceCommand(USER_ID, null));

            earnService.earn(new EarnPointCommand(USER_ID, 1L, null));
        }
    }

    @Nested
    @DisplayName("최초 적립 시 PointUser 자동 생성")
    class AutoCreateOnFirstEarn {

        @Test
        @DisplayName("회원 row 가 없는 상태에서 적립을 시도하면 max_balance=null 인 row 가 자동 생성되어 글로벌 default 한도가 적용된다")
        void earn_creates_user_row_with_null_max_balance() {
            assertThat(userRepository.findById(USER_ID)).isEmpty();

            earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));

            PointUser created = userRepository.findById(USER_ID).orElseThrow();
            assertThat(created.getMaxBalance()).isNull();
            assertThat(created.effectiveMaxBalance(policy.balanceMaxPerUser()))
                .isEqualTo(policy.balanceMaxPerUser());
        }
    }
}
