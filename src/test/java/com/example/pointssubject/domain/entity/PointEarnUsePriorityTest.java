package com.example.pointssubject.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.pointssubject.domain.enums.EarnType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PointEarnUsePriorityTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    @DisplayName("MANUAL 적립이 SYSTEM 보다 먼저 — 만료일이 더 늦더라도")
    void manual_takes_precedence_over_system_even_when_later_expiry() {
        PointEarn manualLate = stub(1L, EarnType.MANUAL, BASE.plusDays(60), BASE);
        PointEarn systemEarly = stub(2L, EarnType.SYSTEM, BASE.plusDays(10), BASE);

        List<PointEarn> sorted = sort(systemEarly, manualLate);

        assertThat(sorted).extracting(PointEarn::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("같은 source 안에서는 만료일이 더 가까운 적립이 먼저")
    void earlier_expiry_first_within_same_source() {
        PointEarn late = stub(1L, EarnType.SYSTEM, BASE.plusDays(60), BASE);
        PointEarn early = stub(2L, EarnType.SYSTEM, BASE.plusDays(10), BASE);

        List<PointEarn> sorted = sort(late, early);

        assertThat(sorted).extracting(PointEarn::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("같은 source + 같은 만료일이면 적립일이 더 빠른 적립이 먼저")
    void earlier_created_first_when_source_and_expiry_equal() {
        LocalDateTime sameExpiry = BASE.plusDays(30);
        PointEarn later = stub(1L, EarnType.SYSTEM, sameExpiry, BASE.plusHours(2));
        PointEarn earlier = stub(2L, EarnType.SYSTEM, sameExpiry, BASE.plusHours(1));

        List<PointEarn> sorted = sort(later, earlier);

        assertThat(sorted).extracting(PointEarn::getId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("source/만료/적립일 모두 동일하면 id ASC 가 tiebreak")
    void id_ascending_breaks_tie_when_all_else_equal() {
        LocalDateTime sameExpiry = BASE.plusDays(30);
        LocalDateTime sameCreated = BASE.plusHours(1);
        PointEarn higher = stub(99L, EarnType.SYSTEM, sameExpiry, sameCreated);
        PointEarn lower = stub(10L, EarnType.SYSTEM, sameExpiry, sameCreated);

        List<PointEarn> sorted = sort(higher, lower);

        assertThat(sorted).extracting(PointEarn::getId).containsExactly(10L, 99L);
    }

    @Test
    @DisplayName("MANUAL 끼리도 우선순위 규칙이 동일 적용 (만료 임박 → 적립일 → id)")
    void manual_internal_ordering_follows_full_chain() {
        PointEarn m1 = stub(1L, EarnType.MANUAL, BASE.plusDays(60), BASE);
        PointEarn m2 = stub(2L, EarnType.MANUAL, BASE.plusDays(10), BASE);
        PointEarn m3 = stub(3L, EarnType.MANUAL, BASE.plusDays(10), BASE.plusHours(1));
        PointEarn s1 = stub(4L, EarnType.SYSTEM, BASE.plusDays(1), BASE);

        List<PointEarn> sorted = sort(s1, m1, m2, m3);

        assertThat(sorted).extracting(PointEarn::getId).containsExactly(2L, 3L, 1L, 4L);
    }

    @Test
    @DisplayName("입력 순서가 달라져도 정렬 결과는 동일")
    void result_is_independent_of_input_order() {
        PointEarn manual = stub(1L, EarnType.MANUAL, BASE.plusDays(30), BASE);
        PointEarn system1 = stub(2L, EarnType.SYSTEM, BASE.plusDays(10), BASE);
        PointEarn system2 = stub(3L, EarnType.SYSTEM, BASE.plusDays(20), BASE);

        List<PointEarn> a = sort(manual, system1, system2);
        List<PointEarn> b = sort(system2, manual, system1);
        List<PointEarn> c = sort(system1, system2, manual);

        assertThat(a).extracting(PointEarn::getId).containsExactly(1L, 2L, 3L);
        assertThat(b).extracting(PointEarn::getId).containsExactly(1L, 2L, 3L);
        assertThat(c).extracting(PointEarn::getId).containsExactly(1L, 2L, 3L);
    }

    private static PointEarn stub(long id, EarnType source, LocalDateTime expiresAt, LocalDateTime createdAt) {
        PointEarn earn = mock(PointEarn.class);
        given(earn.getId()).willReturn(id);
        given(earn.getType()).willReturn(source);
        given(earn.getExpiresAt()).willReturn(expiresAt);
        given(earn.getCreatedAt()).willReturn(createdAt);
        return earn;
    }

    private static List<PointEarn> sort(PointEarn... earns) {
        List<PointEarn> list = new ArrayList<>(List.of(earns));
        list.sort(PointEarn.USE_PRIORITY);
        return list;
    }
}
