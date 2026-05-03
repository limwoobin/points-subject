package com.example.pointssubject.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.repository.PointUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminAcceptanceTest extends AcceptanceTest {

    private static final Long USER_ID = 1L;

    @Autowired private PointUserRepository userRepository;

    @Test
    @DisplayName("관리자가 회원 한도를 변경하면 응답과 DB 모두 새로운 maxBalance 로 갱신된다")
    void 회원_한도_변경_시나리오() throws Exception {
        회원_한도가_변경됨(USER_ID, 5000L);

        em.flush();
        em.clear();
        assertThat(userRepository.findByUserId(USER_ID).orElseThrow().getMaxBalance())
            .isEqualTo(5000L);
    }

    @Test
    @DisplayName("관리자가 maxBalance=null 로 변경하면 override 가 해제되어 max_balance 컬럼이 NULL 이 된다 (글로벌 default 회귀)")
    void 회원_한도_해제_시나리오() throws Exception {
        // 사전: override 5000 으로 설정
        회원_한도가_변경됨(USER_ID, 5000L);
        em.flush();
        em.clear();
        assertThat(userRepository.findByUserId(USER_ID).orElseThrow().getMaxBalance())
            .isEqualTo(5000L);

        // 본: null 로 해제
        회원_한도가_변경됨(USER_ID, null);

        em.flush();
        em.clear();
        assertThat(userRepository.findByUserId(USER_ID).orElseThrow().getMaxBalance())
            .isNull();
    }
}
