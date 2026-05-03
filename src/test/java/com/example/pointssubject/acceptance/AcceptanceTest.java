package com.example.pointssubject.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.pointssubject.controller.dto.BalanceResponse;
import com.example.pointssubject.controller.dto.CancelEarnRequest;
import com.example.pointssubject.controller.dto.CancelEarnResponse;
import com.example.pointssubject.controller.dto.CancelUsePointRequest;
import com.example.pointssubject.controller.dto.CancelUsePointResponse;
import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.controller.dto.PointHistoryResponse;
import com.example.pointssubject.controller.dto.UpdateUserMaxBalanceRequest;
import com.example.pointssubject.controller.dto.UsePointRequest;
import com.example.pointssubject.controller.dto.UsePointResponse;
import com.example.pointssubject.domain.entity.PointEarn;
import com.example.pointssubject.domain.entity.PointUse;
import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.EarnType;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.repository.PointUseRepository;
import com.example.pointssubject.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

public abstract class AcceptanceTest extends AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected PointEarnRepository earnRepository;
    @Autowired protected PointUseRepository useRepository;

    @PersistenceContext
    protected EntityManager em;

    // ── 행위 step ───────────────────────────────────────────────

    protected Long 적립이_요청됨(Long userId, long amount) throws Exception {
        return 적립이_요청됨(userId, amount, null);
    }

    protected Long 적립이_요청됨(Long userId, long amount, Integer expiryDays) throws Exception {
        EarnPointRequest req = new EarnPointRequest(userId, amount, expiryDays);
        String body = mockMvc.perform(post("/api/points/earn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.earnId").isNumber())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.amount").value(amount))
            .andExpect(jsonPath("$.type").value("SYSTEM"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, EarnPointResponse.class).earnId();
    }

    protected Long 수기_적립이_요청됨(Long userId, long amount) throws Exception {
        EarnPointRequest req = new EarnPointRequest(userId, amount, null);
        String body = mockMvc.perform(post("/api/admin/points/earn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.earnId").isNumber())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.amount").value(amount))
            .andExpect(jsonPath("$.type").value("MANUAL"))
            .andExpect(jsonPath("$.expiresAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, EarnPointResponse.class).earnId();
    }

    protected CancelEarnResponse 적립취소가_요청됨(Long userId, Long earnId) throws Exception {
        CancelEarnRequest req = new CancelEarnRequest(userId);
        String body = mockMvc.perform(post("/api/points/earn/{earnId}/cancel", earnId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.earnId").value(earnId))
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CancelEarnResponse.class);
    }

    protected UsePointResponse 사용이_요청됨(Long userId, String orderNumber, long amount) throws Exception {
        UsePointRequest req = new UsePointRequest(userId, orderNumber, amount);
        String body = mockMvc.perform(post("/api/points/use")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.useId").isNumber())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.orderNumber").value(orderNumber))
            .andExpect(jsonPath("$.amount").value(amount))
            .andExpect(jsonPath("$.allocations").isArray())
            .andExpect(jsonPath("$.allocations[0].earnId").isNumber())
            .andExpect(jsonPath("$.allocations[0].amount").isNumber())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, UsePointResponse.class);
    }

    protected CancelUsePointResponse 사용취소가_요청됨(Long userId, String orderNumber, String orderRefundId, long amount) throws Exception {
        CancelUsePointRequest req = new CancelUsePointRequest(userId, orderNumber, orderRefundId, amount);
        String body = mockMvc.perform(post("/api/points/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelId").isNumber())
            .andExpect(jsonPath("$.amount").value(amount))
            .andExpect(jsonPath("$.remainingCancellable").isNumber())
            .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, CancelUsePointResponse.class);
    }

    protected BalanceResponse 잔액_조회됨(Long userId) throws Exception {
        String body = mockMvc.perform(get("/api/points/users/{userId}/balance", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.availableBalance").isNumber())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, BalanceResponse.class);
    }

    protected PointHistoryResponse 이력_조회됨(Long userId) throws Exception {
        return 이력_조회됨(userId, null, null);
    }

    protected PointHistoryResponse 이력_조회됨(Long userId, Integer page, Integer size) throws Exception {
        var req = get("/api/points/users/{userId}/history", userId);
        if (page != null) req = req.param("page", page.toString());
        if (size != null) req = req.param("size", size.toString());
        String body = mockMvc.perform(req)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.page").isNumber())
            .andExpect(jsonPath("$.size").isNumber())
            .andExpect(jsonPath("$.totalElements").isNumber())
            .andExpect(jsonPath("$.totalPages").isNumber())
            .andExpect(jsonPath("$.hasNext").isBoolean())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, PointHistoryResponse.class);
    }

    protected void 회원_한도가_변경됨(Long userId, Long maxBalance) throws Exception {
        UpdateUserMaxBalanceRequest req = new UpdateUserMaxBalanceRequest(maxBalance);
        mockMvc.perform(put("/api/admin/users/{userId}/max-balance", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.maxBalance").value(maxBalance));
    }

    // ── 시간 흐름 step ──────────────────────────────────────────

    /** Clock 추상화 대신 native UPDATE 로 시간 경과 시뮬레이션. */
    protected void 적립이_만료됨(Long earnId) {
        em.flush();
        em.createQuery("UPDATE PointEarn e SET e.expiresAt = :past WHERE e.id = :id")
            .setParameter("past", LocalDateTime.now().minusDays(1))
            .setParameter("id", earnId)
            .executeUpdate();
        em.clear();
    }

    // ── 검증 step ───────────────────────────────────────────────

    protected void 잔액이_확인됨(Long userId, long expected) {
        em.flush();
        em.clear();
        assertThat(earnRepository.sumActiveBalance(userId, LocalDateTime.now()))
            .as("회원 %d 잔액", userId)
            .isEqualTo(expected);
    }

    protected void 적립_잔여가_확인됨(Long earnId, long expected) {
        em.flush();
        em.clear();
        PointEarn earn = earnRepository.findById(earnId).orElseThrow();
        assertThat(earn.getRemainingAmount())
            .as("적립 %d 잔여", earnId)
            .isEqualTo(expected);
    }

    protected void 적립_상태가_확인됨(Long earnId, EarnStatus status) {
        em.flush();
        em.clear();
        assertThat(earnRepository.findById(earnId).orElseThrow().getStatus())
            .isEqualTo(status);
    }

    protected void 적립_type이_확인됨(Long earnId, EarnType source) {
        em.flush();
        em.clear();
        assertThat(earnRepository.findById(earnId).orElseThrow().getType())
            .isEqualTo(source);
    }

    protected void 사용_부분_취소가_확인됨(Long useId) {
        em.flush();
        em.clear();
        PointUse use = useRepository.findById(useId).orElseThrow();
        long cancelled = useRepository.sumCancelledByUseId(useId);
        assertThat(cancelled > 0 && cancelled < use.getAmount())
            .as("사용 %d 부분 취소 상태 (사용=%d, 누적환불=%d)", useId, use.getAmount(), cancelled)
            .isTrue();
    }

    protected void 사용_잔여_환불가능액이_확인됨(Long useId, long expected) {
        em.flush();
        em.clear();
        PointUse use = useRepository.findById(useId).orElseThrow();
        long cancelled = useRepository.sumCancelledByUseId(useId);
        assertThat(use.getAmount() - cancelled)
            .as("사용 %d 잔여 환불 가능", useId)
            .isEqualTo(expected);
    }

    /** cancelId 가 발행한 reissue 적립이 정확히 1건 존재하고 amount 와 일치하는지 검증. 그 reissue 적립을 반환. */
    protected PointEarn 재발급된_적립이_확인됨(Long cancelId, long expectedAmount) {
        em.flush();
        em.clear();
        var reissuedList = earnRepository.findByOriginUseCancelId(cancelId);
        assertThat(reissuedList).hasSize(1);
        PointEarn reissued = reissuedList.get(0);
        assertThat(reissued.getType()).isEqualTo(EarnType.USE_CANCEL_REISSUE);
        assertThat(reissued.getInitialAmount()).isEqualTo(expectedAmount);
        assertThat(reissued.getRemainingAmount()).isEqualTo(expectedAmount);
        return reissued;
    }

}
