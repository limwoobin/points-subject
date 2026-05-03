package com.example.pointssubject.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CancelUsePointRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static CancelUsePointRequest valid() {
        return new CancelUsePointRequest(1L, "ORD-1", "ORF-1", 100L);
    }

    @Test
    @DisplayName("모든 필드가 유효하면 위반이 발생하지 않는다")
    void valid_request_has_no_violations() {
        Set<ConstraintViolation<CancelUsePointRequest>> violations = validator.validate(valid());
        assertThat(violations).isEmpty();
    }

    @Nested
    @DisplayName("userId 제약")
    class UserIdConstraints {

        @Test
        @DisplayName("userId 가 null 이면 @NotNull 위반이 'userId' 필드에서 보고된다")
        void null_user_id() {
            CancelUsePointRequest req = new CancelUsePointRequest(null, "ORD-1", "ORF-1", 100L);
            assertThat(violationFields(req)).contains("userId");
        }
    }

    @Nested
    @DisplayName("orderNumber 제약")
    class OrderNumberConstraints {

        @Test
        @DisplayName("orderNumber 가 null 이면 @NotBlank 위반이 보고된다")
        void null_order_number() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, null, "ORF-1", 100L);
            assertThat(violationFields(req)).contains("orderNumber");
        }

        @Test
        @DisplayName("orderNumber 가 빈 문자열이면 @NotBlank 위반이 보고된다")
        void blank_order_number() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "  ", "ORF-1", 100L);
            assertThat(violationFields(req)).contains("orderNumber");
        }

        @Test
        @DisplayName("orderNumber 가 65자 이상이면 @Size(max=64) 위반이 보고된다")
        void oversized_order_number() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "X".repeat(65), "ORF-1", 100L);
            assertThat(violationFields(req)).contains("orderNumber");
        }
    }

    @Nested
    @DisplayName("orderRefundId 제약")
    class OrderRefundIdConstraints {

        @Test
        @DisplayName("orderRefundId 가 null 이면 @NotBlank 위반이 보고된다")
        void null_order_refund_id() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "ORD-1", null, 100L);
            assertThat(violationFields(req)).contains("orderRefundId");
        }

        @Test
        @DisplayName("orderRefundId 가 빈 문자열이면 @NotBlank 위반이 보고된다")
        void blank_order_refund_id() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "ORD-1", "  ", 100L);
            assertThat(violationFields(req)).contains("orderRefundId");
        }

        @Test
        @DisplayName("orderRefundId 가 65자 이상이면 @Size(max=64) 위반이 보고된다")
        void oversized_order_refund_id() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "ORD-1", "X".repeat(65), 100L);
            assertThat(violationFields(req)).contains("orderRefundId");
        }
    }

    @Nested
    @DisplayName("amount 제약")
    class AmountConstraints {

        @Test
        @DisplayName("amount 가 null 이면 @NotNull 위반이 보고된다")
        void null_amount() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "ORD-1", "ORF-1", null);
            assertThat(violationFields(req)).contains("amount");
        }

        @Test
        @DisplayName("amount 가 0 이면 @Positive 위반이 보고된다")
        void zero_amount() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "ORD-1", "ORF-1", 0L);
            assertThat(violationFields(req)).contains("amount");
        }

        @Test
        @DisplayName("amount 가 음수면 @Positive 위반이 보고된다")
        void negative_amount() {
            CancelUsePointRequest req = new CancelUsePointRequest(1L, "ORD-1", "ORF-1", -1L);
            assertThat(violationFields(req)).contains("amount");
        }
    }

    private static Set<String> violationFields(CancelUsePointRequest req) {
        return validator.validate(req).stream()
            .map(v -> v.getPropertyPath().toString())
            .collect(java.util.stream.Collectors.toSet());
    }
}
