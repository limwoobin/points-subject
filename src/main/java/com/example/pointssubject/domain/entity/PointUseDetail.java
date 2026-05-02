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
 * 사용 ↔ 적립 1원 단위 매핑. 한 PointUse 가 N 개의 PointEarn 으로부터 분배되어 차감된 결과.
 */
@Entity
@Table(
    name = "point_use_detail",
    indexes = {
        @Index(name = "idx_use_detail_use_id", columnList = "use_id, created_at, id"),
        @Index(name = "idx_use_detail_earn_id", columnList = "earn_id")
    }
)
@SQLDelete(sql = "UPDATE point_use_detail SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointUseDetail extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "use_id", nullable = false)
    private Long useId;

    @Column(name = "earn_id", nullable = false)
    private Long earnId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private PointUseDetail(Long useId, Long earnId, Long amount) {
        this.useId = useId;
        this.earnId = earnId;
        this.amount = amount;
    }

    public static PointUseDetail of(Long useId, Long earnId, Long amount) {
        return new PointUseDetail(useId, earnId, amount);
    }
}
