package com.example.pointssubject.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceRequest;
import com.example.pointssubject.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class PointApiErrorE2ETest extends AbstractIntegrationTest {

    private static final Long USER_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Nested
    @DisplayName("도메인 예외 → HTTP 매핑 (PointException 핸들러)")
    class DomainExceptions {

        @Test
        @DisplayName("정책 최댓값을 초과하는 amount(100_001) 로 적립 요청을 보내면 400 + POINT-101 응답이 반환된다")
        void invalid_earn_amount_returns_400() throws Exception {
            EarnPointRequest request = new EarnPointRequest(USER_ID, 100_001L, null);

            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-101"));
        }

        @Test
        @DisplayName("정책 최솟값 미만의 expiryDays(0) 로 적립 요청을 보내면 400 + POINT-102 응답이 반환된다")
        void invalid_expiry_days_returns_400() throws Exception {
            EarnPointRequest request = new EarnPointRequest(USER_ID, 1000L, 0);

            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-102"));
        }

        @Test
        @DisplayName("max_balance=0 으로 차단된 회원이 적립을 시도하면 409 + POINT-103 응답이 반환된다")
        void balance_limit_exceeded_returns_409() throws Exception {
            // PUT 으로 회원 한도 0 설정 (HTTP→HTTP 체이닝, E2E 의도 유지)
            UpdateUserMaxBalanceRequest setLimit = new UpdateUserMaxBalanceRequest(0L);
            mockMvc.perform(put("/api/admin/users/{userId}/max-balance", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(setLimit)))
                .andExpect(status().isOk());

            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1L, null);
            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-103"));
        }

        @Test
        @DisplayName("존재하지 않는 earnId 로 취소 요청을 보내면 404 + POINT-201 응답이 반환된다")
        void earn_not_found_returns_404() throws Exception {
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT-201"));
        }

        @Test
        @DisplayName("이미 취소된 적립에 대해 다시 취소를 요청하면 409 + POINT-202 응답이 반환된다")
        void earn_cancel_not_allowed_returns_409() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            String earnedBody = mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            EarnPointResponse earned = objectMapper.readValue(earnedBody, EarnPointResponse.class);

            // 첫 번째 취소: 성공
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earned.earnId()))
                .andExpect(status().isOk());

            // 두 번째 취소: 거부
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earned.earnId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-202"));
        }
    }

    @Nested
    @DisplayName("Bean Validation → HTTP 매핑 (MethodArgumentNotValidException 핸들러)")
    class BeanValidation {

        @Test
        @DisplayName("userId 가 누락된 적립 요청을 보내면 @NotNull 위반으로 400 + POINT-001 응답과 errors[].field='userId' 가 반환된다")
        void missing_user_id_returns_400_with_field_error() throws Exception {
            // userId 필드 자체를 빼서 @NotNull 위반 트리거
            Map<String, Object> body = new HashMap<>();
            body.put("amount", 1000L);

            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"))
                .andExpect(jsonPath("$.errors[?(@.field=='userId')]").exists());
        }

        @Test
        @DisplayName("maxBalance=-1 로 회원 한도 변경 요청을 보내면 @PositiveOrZero 위반으로 400 + POINT-001 응답과 errors[].field='maxBalance' 가 반환된다")
        void negative_max_balance_returns_400_with_field_error() throws Exception {
            UpdateUserMaxBalanceRequest request = new UpdateUserMaxBalanceRequest(-1L);

            mockMvc.perform(put("/api/admin/users/{userId}/max-balance", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"))
                .andExpect(jsonPath("$.errors[?(@.field=='maxBalance')]").exists());
        }
    }
}
