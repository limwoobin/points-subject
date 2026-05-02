package com.example.pointssubject.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceRequest;
import com.example.pointssubject.controller.dto.UsePointRequest;
import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class PointApiE2ETest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PointEarnRepository earnRepository;
    @Autowired private PointUserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Nested
    @DisplayName("POST /api/points/earn — 적립")
    class Earn {

        @Test
        @DisplayName("유효한 적립 요청을 보내면 201 Created 와 함께 earnId/userId/amount/source/expiresAt 이 응답되고 DB 에 ACTIVE row 가 생성된다")
        void earn_success() throws Exception {
            EarnPointRequest request = new EarnPointRequest(USER_ID, 1000L, 30);

            String responseBody = mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.earnId").isNumber())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.source").value("SYSTEM"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

            EarnPointResponse response = objectMapper.readValue(responseBody, EarnPointResponse.class);

            entityManager.flush();
            entityManager.clear();
            PointEarn saved = earnRepository.findById(response.earnId()).orElseThrow();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getInitialAmount()).isEqualTo(1000L);
            assertThat(saved.getRemainingAmount()).isEqualTo(1000L);
            assertThat(saved.getStatus()).isEqualTo(EarnStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("POST /api/points/earn/{earnId}/cancel — 적립취소")
    class CancelEarn {

        @Test
        @DisplayName("적립 후 곧바로 취소를 호출하면 200 OK 가 응답되고 DB 의 status/remaining/cancelledAt 이 일관되게 갱신된다")
        void cancel_success() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            String earnedBody = mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            EarnPointResponse earned = objectMapper.readValue(earnedBody, EarnPointResponse.class);

            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earned.earnId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnId").value(earned.earnId()))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

            entityManager.flush();
            entityManager.clear();
            PointEarn refreshed = earnRepository.findById(earned.earnId()).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(EarnStatus.CANCELLED);
            assertThat(refreshed.getRemainingAmount()).isEqualTo(0L);
            assertThat(refreshed.getCancelledAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("POST /api/points/use — 사용")
    class Use {

        @Test
        @DisplayName("적립 후 사용 요청을 보내면 201 Created 와 useId/allocations 가 응답되고 적립 row 의 remaining_amount 가 차감된다")
        void use_success() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            String earnedBody = mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            EarnPointResponse earned = objectMapper.readValue(earnedBody, EarnPointResponse.class);

            UsePointRequest useReq = new UsePointRequest(USER_ID, "ORD-E2E-1", 600L);
            mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(useReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.useId").isNumber())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.orderNumber").value("ORD-E2E-1"))
                .andExpect(jsonPath("$.amount").value(600))
                .andExpect(jsonPath("$.allocations[0].earnId").value(earned.earnId()))
                .andExpect(jsonPath("$.allocations[0].amount").value(600));

            entityManager.flush();
            entityManager.clear();
            PointEarn refreshed = earnRepository.findById(earned.earnId()).orElseThrow();
            assertThat(refreshed.getRemainingAmount()).isEqualTo(400L);
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/users/{userId}/max-balance — 회원 한도 변경")
    class UpdateMaxBalance {

        @Test
        @DisplayName("회원 한도 변경 요청을 보내면 200 OK 와 갱신된 maxBalance 가 응답되고 DB 의 point_user.max_balance 가 반영된다")
        void update_max_balance_success() throws Exception {
            UpdateUserMaxBalanceRequest request = new UpdateUserMaxBalanceRequest(5000L);

            mockMvc.perform(put("/api/admin/users/{userId}/max-balance", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.maxBalance").value(5000));

            entityManager.flush();
            entityManager.clear();
            assertThat(userRepository.findById(USER_ID).orElseThrow().getMaxBalance())
                .isEqualTo(5000L);
        }
    }
}
