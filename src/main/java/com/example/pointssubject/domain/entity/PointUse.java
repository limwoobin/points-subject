package com.example.pointssubject.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * 포인트 사용 row. 한 행이 한 주문(order_number)의 사용 결과를 대표한다.
 * <p>
 * {@code cancelled_amount} 는 향후 사용취소(§3.4) 누계용으로 미리 둠 — 현재는 항상 0.
 * order_number 는 application 레벨 (서비스 락 안의 조회) 에서 중복을 차단하므로 DB UK 는 두지 않았다.
 */
@Entity
@Table(
    name = "point_use",
    indexes = {
        @Index(name = "idx_use_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_use_order_number", columnList = "order_number")
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

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_number", length = 64, nullable = false)
    private String orderNumber;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "cancelled_amount", nullable = false)
    private Long cancelledAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private PointUse(Long userId, String orderNumber, Long amount) {
        this.userId = userId;
        this.orderNumber = orderNumber;
        this.amount = amount;
        this.cancelledAmount = 0L;
    }

    public static PointUse use(Long userId, String orderNumber, Long amount) {
        return new PointUse(userId, orderNumber, amount);
    }
}
