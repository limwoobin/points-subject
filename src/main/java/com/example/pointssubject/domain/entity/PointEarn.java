package com.example.pointssubject.domain.entity;

import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.PointOrigin;
import com.example.pointssubject.domain.enums.PointSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

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
    @Column(name = "source", length = 16, nullable = false)
    private PointSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", length = 32, nullable = false)
    private PointOrigin origin;

    @Column(name = "origin_use_cancel_id")
    private Long originUseCancelId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EarnStatus status;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private PointEarn(Long userId,
                      Long initialAmount,
                      PointSource source,
                      PointOrigin origin,
                      Long originUseCancelId,
                      LocalDateTime expiresAt) {
        this.userId = userId;
        this.initialAmount = initialAmount;
        this.remainingAmount = initialAmount;
        this.source = source;
        this.origin = origin;
        this.originUseCancelId = originUseCancelId;
        this.expiresAt = expiresAt;
        this.status = EarnStatus.ACTIVE;
    }

    public static PointEarn earn(Long userId,
                                 Long initialAmount,
                                 PointSource source,
                                 LocalDateTime expiresAt) {
        return new PointEarn(userId, initialAmount, source, PointOrigin.NORMAL, null, expiresAt);
    }

    public static PointEarn reissueFromUseCancel(Long userId,
                                                 Long initialAmount,
                                                 Long originUseCancelId,
                                                 LocalDateTime expiresAt) {
        return new PointEarn(userId, initialAmount, PointSource.SYSTEM, PointOrigin.USE_CANCEL_REISSUE,
            originUseCancelId, expiresAt);
    }

    public boolean isCancellable() {
        return status == EarnStatus.ACTIVE && remainingAmount.equals(initialAmount);
    }

    public boolean isUsable(LocalDateTime now) {
        return status == EarnStatus.ACTIVE && remainingAmount > 0 && now.isBefore(expiresAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * 본 적립건에서 {@code amount} 만큼 사용 차감.
     * 호출 전 {@link #isUsable(LocalDateTime)} 와 잔여액 충분 여부를 service 단에서 선검증해야 한다.
     */
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

    /** 호출 전 {@link #isCancellable()} 선검증 필수. 본 메소드는 invariant 의 마지막 안전망. */
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
