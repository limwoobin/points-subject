package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceCommand;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceResult;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PointUserCommandServiceUnitTest {

    private static final Long USER_ID = 1L;

    private final PointUserRepository userRepository = mock(PointUserRepository.class);
    private final PointUserCommandService service = new PointUserCommandService(userRepository);

    @Nested
    @DisplayName("회원 한도 변경 성공")
    class UpdateMaxBalanceSuccess {

        @Test
        @DisplayName("기존 회원의 한도를 변경하면 도메인의 updateMaxBalance 가 호출되고 save 는 호출되지 않는다 (dirty checking 의도)")
        void updates_existing_user_via_dirty_checking() {
            PointUser existing = PointUser.create(USER_ID);
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(existing));

            UpdateUserMaxBalanceResult result = service.updateMaxBalance(
                new UpdateUserMaxBalanceCommand(USER_ID, 5000L));

            assertThat(existing.getMaxBalance()).isEqualTo(5000L);
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.maxBalance()).isEqualTo(5000L);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("회원 row 가 없을 때 한도를 변경하면 PointUser.create 후 save 가 호출된다")
        void creates_user_row_when_absent_then_updates() {
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());
            given(userRepository.save(any(PointUser.class)))
                .willAnswer(inv -> inv.getArgument(0));

            UpdateUserMaxBalanceResult result = service.updateMaxBalance(
                new UpdateUserMaxBalanceCommand(USER_ID, 3000L));

            ArgumentCaptor<PointUser> captor = ArgumentCaptor.forClass(PointUser.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(result.maxBalance()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("maxBalance=null 로 변경하면 override 가 해제되어 글로벌 default 로 회귀한다")
        void null_max_balance_resets_override_to_global_default() {
            PointUser existing = PointUser.create(USER_ID);
            existing.updateMaxBalance(7777L);
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(existing));

            UpdateUserMaxBalanceResult result = service.updateMaxBalance(
                new UpdateUserMaxBalanceCommand(USER_ID, null));

            assertThat(existing.getMaxBalance()).isNull();
            assertThat(result.maxBalance()).isNull();
        }

        @Test
        @DisplayName("maxBalance=0 은 '적립 차단' 의도로 그대로 저장된다")
        void zero_max_balance_persists_as_block_signal() {
            PointUser existing = PointUser.create(USER_ID);
            given(userRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(existing));

            UpdateUserMaxBalanceResult result = service.updateMaxBalance(
                new UpdateUserMaxBalanceCommand(USER_ID, 0L));

            assertThat(existing.getMaxBalance()).isEqualTo(0L);
            assertThat(result.maxBalance()).isEqualTo(0L);
        }
    }
}
