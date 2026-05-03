package com.example.pointssubject.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pointssubject.controller.dto.CancelEarnRequest;
import com.example.pointssubject.controller.dto.CancelUsePointRequest;
import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceRequest;
import com.example.pointssubject.controller.dto.UsePointRequest;
import com.example.pointssubject.controller.dto.UsePointResponse;
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
            CancelEarnRequest cancelReq = new CancelEarnRequest(USER_ID);
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", 999_999L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelReq)))
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

            CancelEarnRequest cancelReq = new CancelEarnRequest(USER_ID);
            // 첫 번째 취소: 성공
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earned.earnId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk());

            // 두 번째 취소: 거부
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earned.earnId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-202"));
        }

        @Test
        @DisplayName("다른 회원의 earnId 로 취소를 시도하면 404 + POINT-201 (존재 노출 차단)")
        void earn_ownership_mismatch_returns_404() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            String earnedBody = mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            EarnPointResponse earned = objectMapper.readValue(earnedBody, EarnPointResponse.class);

            Long otherUserId = USER_ID + 1;
            CancelEarnRequest cancelReq = new CancelEarnRequest(otherUserId);
            mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earned.earnId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT-201"));
        }

        @Test
        @DisplayName("적립 없이 사용을 시도하면 잔액 부족으로 409 + POINT-301 응답이 반환된다")
        void use_insufficient_balance_returns_409() throws Exception {
            UsePointRequest useReq = new UsePointRequest(USER_ID, "ORD-NOBAL", 1000L);
            mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(useReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-301"));
        }

        @Test
        @DisplayName("같은 orderNumber 로 두 번 사용 요청을 보내면 두 번째 호출은 409 + POINT-302 응답이 반환된다")
        void duplicate_order_number_returns_409() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated());

            UsePointRequest useReq = new UsePointRequest(USER_ID, "ORD-DUP-E2E", 100L);
            mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(useReq)))
                .andExpect(status().isCreated());

            mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(useReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-302"));
        }

        @Test
        @DisplayName("존재하지 않는 orderNumber 로 사용취소를 요청하면 404 + POINT-401 응답이 반환된다")
        void use_not_found_returns_404() throws Exception {
            CancelUsePointRequest cancelReq = new CancelUsePointRequest(USER_ID, "ORD-MISSING", "ORF-NF", 100L);
            mockMvc.perform(post("/api/points/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POINT-401"));
        }

        @Test
        @DisplayName("취소 가능 잔액을 초과하는 사용취소를 요청하면 409 + POINT-402 응답이 반환된다")
        void use_cancel_amount_exceeded_returns_409() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated());

            UsePointRequest useReq = new UsePointRequest(USER_ID, "ORD-OVER-E2E", 600L);
            String usedBody = mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(useReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
            objectMapper.readValue(usedBody, UsePointResponse.class);

            CancelUsePointRequest over = new CancelUsePointRequest(USER_ID, "ORD-OVER-E2E", "ORF-OVER", 700L);
            mockMvc.perform(post("/api/points/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(over)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-402"));
        }

        @Test
        @DisplayName("이미 처리된 orderRefundId 로 재요청하면 409 + POINT-403 (ORDER_REFUND_ID_DUPLICATED) 가 반환된다")
        void duplicate_refund_id_returns_409() throws Exception {
            EarnPointRequest earnReq = new EarnPointRequest(USER_ID, 1000L, null);
            mockMvc.perform(post("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(earnReq)))
                .andExpect(status().isCreated());

            UsePointRequest useReq = new UsePointRequest(USER_ID, "ORD-CKC-E2E", 600L);
            mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(useReq)))
                .andExpect(status().isCreated());

            // 첫 호출: 100 환불 처리됨
            mockMvc.perform(post("/api/points/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CancelUsePointRequest(USER_ID, "ORD-CKC-E2E", "ORF-CONF", 100L))))
                .andExpect(status().isOk());

            // 같은 키 재요청 → 409 거부 (페이로드 동일 여부 무관)
            mockMvc.perform(post("/api/points/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new CancelUsePointRequest(USER_ID, "ORD-CKC-E2E", "ORF-CONF", 200L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POINT-403"));
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

        @Test
        @DisplayName("amount=0 으로 사용 요청을 보내면 @Positive 위반으로 400 + POINT-001 응답과 errors[].field='amount' 가 반환된다")
        void zero_use_amount_returns_400_with_field_error() throws Exception {
            UsePointRequest request = new UsePointRequest(USER_ID, "ORD-AMOUNT-ZERO", 0L);

            mockMvc.perform(post("/api/points/use")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"))
                .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
        }
    }

    @Nested
    @DisplayName("Path/Query 파라미터 검증 (@Validated + ConstraintViolationException 핸들러)")
    class PathAndQueryValidation {

        @Test
        @DisplayName("userId=0 인 잔액 조회 요청은 @Positive 위반으로 400 + POINT-001 응답이 반환된다")
        void zero_user_id_in_path_returns_400() throws Exception {
            mockMvc.perform(get("/api/points/users/0/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("earnId=-1 인 적립취소 요청은 @Positive 위반으로 400 + POINT-001 응답이 반환된다")
        void negative_earn_id_in_path_returns_400() throws Exception {
            mockMvc.perform(post("/api/points/earn/-1/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("page=-1 인 이력 조회 요청은 @PositiveOrZero 위반으로 400 + POINT-001 응답이 반환된다")
        void negative_page_returns_400() throws Exception {
            mockMvc.perform(get("/api/points/users/{userId}/history", USER_ID).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("size=0 인 이력 조회 요청은 @Positive 위반으로 400 + POINT-001 응답이 반환된다")
        void zero_size_returns_400() throws Exception {
            mockMvc.perform(get("/api/points/users/{userId}/history", USER_ID).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("size 가 정책 상한 100 을 초과하면 service 단 검증으로 400 + POINT-001 응답이 반환된다")
        void oversized_size_returns_400() throws Exception {
            mockMvc.perform(get("/api/points/users/{userId}/history", USER_ID).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("userId 자리에 숫자가 아닌 값이 오면 MethodArgumentTypeMismatch 핸들러가 400 + POINT-001 응답을 반환한다")
        void non_numeric_user_id_returns_400() throws Exception {
            mockMvc.perform(get("/api/points/users/not-a-number/balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("정의되지 않은 경로로 요청하면 NoHandlerFound 핸들러가 400 + POINT-001 응답을 반환한다")
        void unknown_path_returns_400() throws Exception {
            mockMvc.perform(get("/api/nonexistent/path"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }

        @Test
        @DisplayName("지원하지 않는 HTTP 메서드로 요청하면 MethodNotSupported 핸들러가 400 + POINT-001 응답을 반환한다")
        void unsupported_method_returns_400() throws Exception {
            mockMvc.perform(put("/api/points/earn")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POINT-001"));
        }
    }
}
