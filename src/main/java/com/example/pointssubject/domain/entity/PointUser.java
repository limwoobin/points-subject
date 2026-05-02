package com.example.pointssubject.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "point_user")
@SQLDelete(sql = "UPDATE point_user SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUser extends BaseEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "max_balance")
    private Long maxBalance;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private PointUser(Long userId, Long maxBalance) {
        this.userId = userId;
        this.maxBalance = maxBalance;
    }

    public static PointUser create(Long userId) {
        return new PointUser(userId, null);
    }

    public long effectiveMaxBalance(long globalDefault) {
        return maxBalance != null ? maxBalance : globalDefault;
    }

    /** {@code null} 이면 override 해제 (글로벌 default 회귀). 음수 차단은 Bean Validation 단에서 선처리. */
    public void updateMaxBalance(Long newMaxBalance) {
        this.maxBalance = newMaxBalance;
    }
}
