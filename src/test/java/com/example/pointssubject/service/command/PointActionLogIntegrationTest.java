package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pointssubject.domain.entity.PointActionLog;
import com.example.pointssubject.domain.enums.PointActionType;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.repository.PointActionLogRepository;
import com.example.pointssubject.service.command.dto.CancelEarnCommand;
import com.example.pointssubject.service.command.dto.CancelUsePointCommand;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import com.example.pointssubject.service.command.dto.UsePointCommand;
import com.example.pointssubject.service.command.dto.UsePointResult;
import com.example.pointssubject.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PointActionLogIntegrationTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointEarnCommandService earnService;
    @Autowired private PointUseCommandService useService;
    @Autowired private PointActionLogRepository logRepository;

    @Test
    @DisplayName("earn() 호출 시 EARN 액션 로그가 적립 id/amount 와 함께 1건 추가된다")
    void earn_writes_earn_action_log() {
        EarnPointResult earned = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));

        List<PointActionLog> logs = logRepository.findByUserIdOrderByCreatedAtAsc(USER_ID);

        assertThat(logs).hasSize(1);
        PointActionLog log = logs.get(0);
        assertThat(log.getActionType()).isEqualTo(PointActionType.EARN);
        assertThat(log.getUserId()).isEqualTo(USER_ID);
        assertThat(log.getPointEarnId()).isEqualTo(earned.earnId());
        assertThat(log.getPointUseId()).isNull();
        assertThat(log.getAmount()).isEqualTo(1000L);
        assertThat(log.getOrderNumber()).isNull();
        assertThat(log.getOrderRefundId()).isNull();
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("earnManual() 도 EARN 액션 로그를 남긴다 (source 와 무관하게 행위 단위 기록)")
    void earn_manual_also_writes_earn_action_log() {
        earnService.earnManual(new EarnPointCommand(USER_ID, 500L, null));

        List<PointActionLog> logs = logRepository.findByUserIdAndActionTypeOrderByCreatedAtAsc(USER_ID, PointActionType.EARN);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAmount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("cancelEarn() 호출 시 EARN_CANCEL 액션 로그가 1건 추가되어 EARN 과 짝을 이룬다")
    void cancel_earn_writes_earn_cancel_action_log() {
        EarnPointResult earned = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
        earnService.cancelEarn(new CancelEarnCommand(USER_ID, earned.earnId()));

        List<PointActionLog> logs = logRepository.findByUserIdOrderByCreatedAtAsc(USER_ID);

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(PointActionLog::getActionType)
            .containsExactly(PointActionType.EARN, PointActionType.EARN_CANCEL);

        PointActionLog cancelLog = logs.get(1);
        assertThat(cancelLog.getPointEarnId()).isEqualTo(earned.earnId());
        assertThat(cancelLog.getAmount()).isEqualTo(1000L);
        assertThat(cancelLog.getPointUseId()).isNull();
    }

    @Test
    @DisplayName("use() 호출 시 USE 액션 로그가 use id / orderNumber / amount 와 함께 1건 추가된다")
    void use_writes_use_action_log() {
        earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
        UsePointResult used = useService.use(new UsePointCommand(USER_ID, "ORD-1", 700L));

        List<PointActionLog> logs = logRepository
            .findByUserIdAndActionTypeOrderByCreatedAtAsc(USER_ID, PointActionType.USE);

        assertThat(logs).hasSize(1);
        PointActionLog log = logs.get(0);
        assertThat(log.getPointUseId()).isEqualTo(used.useId());
        assertThat(log.getPointEarnId()).isNull();
        assertThat(log.getAmount()).isEqualTo(700L);
        assertThat(log.getOrderNumber()).isEqualTo("ORD-1");
        assertThat(log.getOrderRefundId()).isNull();
    }

    @Test
    @DisplayName("cancelUse() 호출 시 USE_CANCEL 액션 로그가 cancel use id / orderRefundId / amount 와 함께 1건 추가된다")
    void cancel_use_writes_use_cancel_action_log() {
        earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
        useService.use(new UsePointCommand(USER_ID, "ORD-1", 700L));
        useService.cancelUse(new CancelUsePointCommand(USER_ID, "ORD-1", "REF-1", 300L));

        List<PointActionLog> logs = logRepository
            .findByUserIdAndActionTypeOrderByCreatedAtAsc(USER_ID, PointActionType.USE_CANCEL);

        assertThat(logs).hasSize(1);
        PointActionLog log = logs.get(0);
        assertThat(log.getAmount()).isEqualTo(300L);
        assertThat(log.getOrderNumber()).isEqualTo("ORD-1");
        assertThat(log.getOrderRefundId()).isEqualTo("REF-1");
        assertThat(log.getPointEarnId()).isNull();
        assertThat(log.getPointUseId()).isNotNull();
    }

    @Test
    @DisplayName("이미 처리된 orderRefundId 로 재요청하면 reject 되므로 USE_CANCEL 액션 로그는 1건만 남는다")
    void duplicate_cancel_use_does_not_double_log() {
        earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
        useService.use(new UsePointCommand(USER_ID, "ORD-1", 700L));
        useService.cancelUse(new CancelUsePointCommand(USER_ID, "ORD-1", "REF-1", 300L));

        // 두 번째 호출은 ORDER_REFUND_ID_DUPLICATED 로 reject — 로그 추가 없음
        assertThatThrownBy(() -> useService.cancelUse(
                new CancelUsePointCommand(USER_ID, "ORD-1", "REF-1", 300L)))
            .isInstanceOf(PointException.class);

        List<PointActionLog> logs = logRepository
            .findByUserIdAndActionTypeOrderByCreatedAtAsc(USER_ID, PointActionType.USE_CANCEL);
        assertThat(logs).hasSize(1);
    }

    @Test
    @DisplayName("4가지 액션을 순차 실행하면 4건의 로그가 시간순으로 EARN → EARN_CANCEL → EARN → USE → USE_CANCEL 형태로 누적된다")
    void all_four_actions_accumulate_in_chronological_order() {
        EarnPointResult earned1 = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
        earnService.cancelEarn(new CancelEarnCommand(USER_ID, earned1.earnId()));
        earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
        useService.use(new UsePointCommand(USER_ID, "ORD-1", 700L));
        useService.cancelUse(new CancelUsePointCommand(USER_ID, "ORD-1", "REF-1", 300L));

        List<PointActionLog> logs = logRepository.findByUserIdOrderByCreatedAtAsc(USER_ID);
        assertThat(logs).extracting(PointActionLog::getActionType).containsExactly(
            PointActionType.EARN,
            PointActionType.EARN_CANCEL,
            PointActionType.EARN,
            PointActionType.USE,
            PointActionType.USE_CANCEL
        );
    }
}
