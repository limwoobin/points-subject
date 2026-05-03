package com.example.pointssubject.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.EarnType;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.policy.PointPolicyService;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.CancelEarnCommand;
import com.example.pointssubject.service.command.dto.EarnPointCommand;
import com.example.pointssubject.service.command.dto.EarnPointResult;
import com.example.pointssubject.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EarnPointCommandServiceTest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointEarnCommandService earnService;
    @Autowired private PointEarnRepository earnRepository;
    @Autowired private PointUserRepository userRepository;
    @Autowired private PointPolicyService policy;

    @Nested
    @DisplayName("적립 성공")
    class Success {

        @Test
        @DisplayName("적립을 호출하면 PointEarn 이 생성되고 remaining=initial, status=ACTIVE, origin=NORMAL 로 저장된다")
        void earn_creates_point_earn_row() {
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));

            assertThat(result.earnId()).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.amount()).isEqualTo(1000L);
            assertThat(result.type()).isEqualTo(EarnType.SYSTEM);

            PointEarn saved = earnRepository.findById(result.earnId()).orElseThrow();
            assertThat(saved.getInitialAmount()).isEqualTo(1000L);
            assertThat(saved.getRemainingAmount()).isEqualTo(1000L);
            assertThat(saved.getStatus()).isEqualTo(EarnStatus.ACTIVE);
            assertThat(saved.getType()).isEqualTo(EarnType.SYSTEM);
            assertThat(saved.getCancelledAt()).isNull();
        }
    }

    @Nested
    @DisplayName("1회 적립 금액 경계")
    class EarnAmountLimit {

        @Test
        @DisplayName("정책 최솟값(1) 으로 적립을 시도하면 적립이 성공한다")
        void earn_with_min_amount_succeeds() {
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, policy.earnMin(), null));
            assertThat(result.amount()).isEqualTo(policy.earnMin());
        }

        @Test
        @DisplayName("정책 최댓값(100,000) 으로 적립을 시도하면 적립이 성공한다")
        void earn_with_max_amount_succeeds() {
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, policy.earnMax(), null));
            assertThat(result.amount()).isEqualTo(policy.earnMax());
        }
    }

    @Nested
    @DisplayName("회원 보유 한도 — 글로벌 default 와 개인 override")
    class BalanceLimit {

        @Test
        @DisplayName("회원에 max_balance override 가 있으면 글로벌 default 가 아닌 override 값으로 한도가 적용된다")
        void user_override_takes_precedence() {
            long userOverride = 500L;
            userRepository.saveAndFlush(new PointUserTestFactory(USER_ID, userOverride).build());

            earnService.earn(new EarnPointCommand(USER_ID, userOverride, null));

            assertThatThrownBy(() ->
                earnService.earn(new EarnPointCommand(USER_ID, 1L, null)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("잔액과 신규 적립의 합이 한도와 정확히 같으면 적립이 성공하고 1원이라도 추가하면 거부된다")
        void earn_at_exact_limit_succeeds() {
            long override = 1000L;
            userRepository.saveAndFlush(new PointUserTestFactory(USER_ID, override).build());

            earnService.earn(new EarnPointCommand(USER_ID, 600L, null));
            EarnPointResult last = earnService.earn(
                new EarnPointCommand(USER_ID, 400L, null));
            assertThat(last.amount()).isEqualTo(400L);

            assertThatThrownBy(() ->
                earnService.earn(new EarnPointCommand(USER_ID, 1L, null)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("한 회원의 한도가 가득 차도 다른 회원의 적립에는 영향이 없다")
        void user_limits_are_isolated_per_user() {
            Long otherUserId = 2L;
            long override = 500L;
            userRepository.saveAndFlush(new PointUserTestFactory(USER_ID, override).build());

            earnService.earn(new EarnPointCommand(USER_ID, 500L, null));
            assertThatThrownBy(() ->
                earnService.earn(new EarnPointCommand(USER_ID, 1L, null)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED);

            EarnPointResult other = earnService.earn(
                new EarnPointCommand(otherUserId, 1000L, null));
            assertThat(other.amount()).isEqualTo(1000L);
        }

        @Test
        @DisplayName("한도 도달 상태에서 적립을 취소하면 해당 금액만큼 한도가 회복되어 재적립이 가능해진다")
        void cancel_frees_quota_for_reearn() {
            long override = 1000L;
            userRepository.saveAndFlush(new PointUserTestFactory(USER_ID, override).build());

            EarnPointResult e1 = earnService.earn(
                new EarnPointCommand(USER_ID, override, null));
            assertThatThrownBy(() ->
                earnService.earn(new EarnPointCommand(USER_ID, 1L, null)))
                .isInstanceOf(PointException.class)
                .extracting("errorCode").isEqualTo(PointErrorCode.EARN_BALANCE_LIMIT_EXCEEDED);

            earnService.cancelEarn(new CancelEarnCommand(USER_ID, e1.earnId()));

            EarnPointResult re = earnService.earn(
                new EarnPointCommand(USER_ID, override, null));
            assertThat(re.amount()).isEqualTo(override);
        }
    }

    @Nested
    @DisplayName("적립건 단위 추적성")
    class Traceability {

        @Test
        @DisplayName("두 번 적립하면 각자 다른 id 를 가진 row 가 생성되고 amount/remaining 을 독립 보유한다")
        void earn_row_owns_amount_and_remaining_independently() {
            EarnPointResult r1 = earnService.earn(new EarnPointCommand(USER_ID, 1000L, null));
            EarnPointResult r2 = earnService.earn(new EarnPointCommand(USER_ID,  500L, null));

            assertThat(r1.earnId()).isNotEqualTo(r2.earnId());

            PointEarn e1 = earnRepository.findById(r1.earnId()).orElseThrow();
            PointEarn e2 = earnRepository.findById(r2.earnId()).orElseThrow();
            assertThat(e1.getRemainingAmount()).isEqualTo(1000L);
            assertThat(e2.getRemainingAmount()).isEqualTo(500L);
        }
    }

    // SYSTEM/MANUAL source 식별 happy path 는 EarnAcceptanceTest 가 HTTP layer 에서 검증.
    // earnManual 진입점의 도메인 보장은 PointEarnCommandServiceUnitTest.earn_manual_persists_with_manual_source 단위 테스트.

    @Nested
    @DisplayName("만료일 부여 (1일 이상 ~ 5년 미만)")
    class Expiry {

        @Test
        @DisplayName("expiryDays 를 지정하지 않으면 정책의 default-days(365) 가 적용된다")
        void default_expiry_days_when_not_specified() {
            LocalDateTime before = LocalDateTime.now();
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));
            LocalDateTime after = LocalDateTime.now();

            long days = ChronoUnit.DAYS.between(before.toLocalDate(),
                result.expiresAt().toLocalDate());
            long daysAfter = ChronoUnit.DAYS.between(after.toLocalDate(),
                result.expiresAt().toLocalDate());
            assertThat(days).isBetween((long) policy.expiryDefaultDays() - 1, (long) policy.expiryDefaultDays());
            assertThat(daysAfter).isBetween((long) policy.expiryDefaultDays() - 1, (long) policy.expiryDefaultDays());
        }

        @Test
        @DisplayName("expiryDays 를 명시하면 해당 일수만큼의 만료일이 적용된다")
        void custom_expiry_days_applied() {
            LocalDateTime before = LocalDateTime.now();
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, 30));

            long minutesGap = ChronoUnit.MINUTES.between(before.plusDays(30), result.expiresAt());
            assertThat(minutesGap).isBetween(-1L, 1L);
        }

        @Test
        @DisplayName("expiryDays 가 정책 최솟값(1) 이면 적립이 성공한다 (inclusive)")
        void min_days_inclusive_succeeds() {
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, policy.expiryMinDays()));
            assertThat(result.expiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("expiryDays 가 정책 최댓값-1(1824) 이면 적립이 성공한다 (exclusive 경계)")
        void max_days_exclusive_minus_one_succeeds() {
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, policy.expiryMaxDays() - 1));
            assertThat(result.expiresAt()).isAfter(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("audit 컬럼 + soft-delete")
    class AuditAndSoftDelete {

        // audit 자동 채움은 PointEarnRepositoryTest.HibernateAnnotations.audit_columns_populated_on_save 에서 (DataJpaTest, 더 가벼움) 검증.
        // soft-delete 가 sumActiveBalance 에서 제외되는 것은 PointEarnRepositoryTest.SumActiveBalance.excludes_soft_deleted_earn 에서 검증.

        @Test
        @DisplayName("softDelete() 를 호출하면 deletedAt 이 채워지고 isDeleted() 가 true 를 반환한다")
        void soft_delete_sets_deleted_at() {
            EarnPointResult result = earnService.earn(
                new EarnPointCommand(USER_ID, 1000L, null));

            PointEarn earn = earnRepository.findById(result.earnId()).orElseThrow();
            earn.softDelete();

            assertThat(earn.getDeletedAt()).isNotNull();
            assertThat(earn.isDeleted()).isTrue();
        }
    }

    /** maxBalance 가 non-null 인 PointUser 생성용 — production factory 는 null 만 허용. */
    private static class PointUserTestFactory {
        private final Long userId;
        private final Long maxBalance;

        PointUserTestFactory(Long userId, Long maxBalance) {
            this.userId = userId;
            this.maxBalance = maxBalance;
        }

        PointUser build() {
            try {
                var ctor = PointUser.class.getDeclaredConstructor(Long.class, Long.class);
                ctor.setAccessible(true);
                return ctor.newInstance(userId, maxBalance);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
