package com.example.pointssubject.domain.entity;

import com.example.pointssubject.domain.enums.PointActionType;
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

/** append-only 로그 — 적립/적립취소/사용/사용취소 통합 시계열. */
@Entity
@Table(
    name = "point_action_log",
    indexes = {
        @Index(name = "idx_action_log_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_action_log_type_created", columnList = "action_type, created_at"),
        @Index(name = "idx_action_log_earn_id", columnList = "point_earn_id"),
        @Index(name = "idx_action_log_use_id", columnList = "point_use_id")
    }
)
@SQLDelete(sql = "UPDATE point_action_log SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointActionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 32, nullable = false)
    private PointActionType actionType;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "point_earn_id")
    private Long pointEarnId;

    @Column(name = "point_use_id")
    private Long pointUseId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "order_number", length = 64)
    private String orderNumber;

    @Column(name = "order_refund_id", length = 64)
    private String orderRefundId;

    private PointActionLog(PointActionType actionType,
                           Long userId,
                           Long pointEarnId,
                           Long pointUseId,
                           Long amount,
                           String orderNumber,
                           String orderRefundId) {
        this.actionType = actionType;
        this.userId = userId;
        this.pointEarnId = pointEarnId;
        this.pointUseId = pointUseId;
        this.amount = amount;
        this.orderNumber = orderNumber;
        this.orderRefundId = orderRefundId;
    }

    public static PointActionLog earn(Long userId, Long pointEarnId, Long amount) {
        return new PointActionLog(PointActionType.EARN, userId, pointEarnId, null, amount, null, null);
    }

    public static PointActionLog earnCancel(Long userId, Long pointEarnId, Long amount) {
        return new PointActionLog(PointActionType.EARN_CANCEL, userId, pointEarnId, null, amount, null, null);
    }

    public static PointActionLog use(Long userId, Long pointUseId, Long amount, String orderNumber) {
        return new PointActionLog(PointActionType.USE, userId, null, pointUseId, amount, orderNumber, null);
    }

    public static PointActionLog useCancel(Long userId,
                                           Long pointUseId,
                                           Long amount,
                                           String orderNumber,
                                           String orderRefundId) {
        return new PointActionLog(PointActionType.USE_CANCEL, userId, null, pointUseId, amount, orderNumber, orderRefundId);
    }
}
