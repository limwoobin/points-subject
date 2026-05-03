package com.example.pointssubject.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/** 회원별 정책. userId 는 자연키 UK. */
@Entity
@Table(
    name = "point_user",
    uniqueConstraints = @UniqueConstraint(name = "uk_point_user_user_id", columnNames = "user_id")
)
@SQLDelete(sql = "UPDATE point_user SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "max_balance")
    private Long maxBalance;

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

    public void updateMaxBalance(Long newMaxBalance) {
        this.maxBalance = newMaxBalance;
    }
}
