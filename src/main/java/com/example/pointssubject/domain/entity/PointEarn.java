package com.example.pointssubject.domain.entity;

import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.EarnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;

@Entity
@Table(
    name = "point_earn",
    indexes = {
        @Index(name = "idx_earn_user_active_expiry", columnList = "user_id, status, expires_at"),
        @Index(name = "idx_earn_origin_use_cancel", columnList = "origin_use_cancel_id")
    }
)
@SQLDelete(sql = "UPDATE point_earn SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Audited
@AuditOverride(forClass = BaseEntity.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointEarn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Long initialAmount;

    @Column(name = "remaining_amount", nullable = false)
    private Long remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16, nullable = false)
    private EarnType type;

    @Column(name = "origin_use_cancel_id")
    private Long originUseCancelId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EarnStatus status;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 우선순위: MANUAL → 만료 임박 → 적립일 → id (createdAt 동률 tiebreak). */
    public static final Comparator<PointEarn> USE_PRIORITY = Comparator
            .comparingInt((PointEarn e) -> e.getType() == EarnType.MANUAL ? 0 : 1)
            .thenComparing(PointEarn::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PointEarn::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PointEarn::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private PointEarn(Long userId,
                      Long initialAmount,
                      EarnType type,
                      Long originUseCancelId,
                      LocalDateTime expiresAt) {
        this.userId = userId;
        this.initialAmount = initialAmount;
        this.remainingAmount = initialAmount;
        this.type = type;
        this.originUseCancelId = originUseCancelId;
        this.expiresAt = expiresAt;
        this.status = EarnStatus.ACTIVE;
    }

    public static PointEarn earn(Long userId,
                                 Long initialAmount,
                                 EarnType type,
                                 LocalDateTime expiresAt) {
        return new PointEarn(userId, initialAmount, type, null, expiresAt);
    }

    public static PointEarn reissueFromUseCancel(Long userId,
                                                 Long initialAmount,
                                                 Long originUseCancelId,
                                                 LocalDateTime expiresAt) {
        return new PointEarn(userId, initialAmount, EarnType.USE_CANCEL_REISSUE, originUseCancelId, expiresAt);
    }

    public boolean isCancellable() {
        return status == EarnStatus.ACTIVE && remainingAmount.equals(initialAmount);
    }

    public boolean isAlive(LocalDateTime now) {
        return status == EarnStatus.ACTIVE && now.isBefore(expiresAt);
    }

    public void useFrom(long amount) {
        if (amount <= 0 || amount > remainingAmount || status != EarnStatus.ACTIVE) {
            throw new IllegalStateException(
                "PointEarn cannot deduct: id=" + id
                    + ", status=" + status
                    + ", remaining=" + remainingAmount
                    + ", requested=" + amount);
        }

        this.remainingAmount -= amount;
    }

    public void restoreFromUseCancel(long amount) {
        if (amount <= 0 || status != EarnStatus.ACTIVE) {
            throw new IllegalStateException(
                "PointEarn cannot restore: id=" + id
                    + ", status=" + status
                    + ", requested=" + amount);
        }

        long after = remainingAmount + amount;
        if (after > initialAmount) {
            throw new IllegalStateException(
                "PointEarn restore overflow: id=" + id
                    + ", remaining=" + remainingAmount
                    + ", initial=" + initialAmount
                    + ", requested=" + amount);
        }

        this.remainingAmount = after;
    }

    public void cancel(LocalDateTime now) {
        if (!isCancellable()) {
            throw new IllegalStateException(
                "PointEarn not cancellable: id=" + id
                    + ", status=" + status
                    + ", remaining=" + remainingAmount
                    + ", initial=" + initialAmount);
        }

        this.status = EarnStatus.CANCELLED;
        this.remainingAmount = 0L;
        this.cancelledAt = now;
    }
}
