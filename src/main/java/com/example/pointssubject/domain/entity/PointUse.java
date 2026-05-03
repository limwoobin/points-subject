package com.example.pointssubject.domain.entity;

import com.example.pointssubject.domain.enums.PointUseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * USE / USE_CANCEL 통합 테이블 (sparse-column)
 */
@Entity
@Table(
    name = "point_use",
    indexes = {
        @Index(name = "idx_use_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_use_order_number", columnList = "order_number"),
        @Index(name = "idx_use_target_use_id", columnList = "target_use_id"),
        @Index(name = "idx_use_order_refund_id", columnList = "order_refund_id")
    }
)
@SQLDelete(sql = "UPDATE point_use SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16, nullable = false)
    private PointUseType type;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", length = 64, nullable = false)
    private String orderNumber;

    /** USE: 차감액 / USE_CANCEL: 환불액. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "target_use_id")
    private Long targetUseId;

    @Column(name = "order_refund_id", length = 64)
    private String orderRefundId;

    private PointUse(PointUseType type,
                     Long userId,
                     String orderNumber,
                     Long amount,
                     Long targetUseId,
                     String orderRefundId) {
        this.type = type;
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.amount = amount;
        this.targetUseId = targetUseId;
        this.orderRefundId = orderRefundId;
    }

    public static PointUse use(Long userId, String orderNumber, Long amount) {
        return new PointUse(PointUseType.USE, userId, orderNumber, amount, null, null);
    }

    public static PointUse useCancel(Long userId,
                                     String orderNumber,
                                     Long targetUseId,
                                     String orderRefundId,
                                     Long amount) {
        return new PointUse(PointUseType.USE_CANCEL, userId, orderNumber, amount, targetUseId, orderRefundId);
    }

    public boolean isUse() {
        return type == PointUseType.USE;
    }

    public boolean isUseCancel() {
        return type == PointUseType.USE_CANCEL;
    }
}
