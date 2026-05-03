package com.example.pointssubject.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pointssubject.domain.enums.EarnStatus;
import com.example.pointssubject.domain.enums.EarnType;
import com.example.pointssubject.repository.PointEarnRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Envers 는 commit 직전 flush — 롤백 기반 통합 테스트로는 검증 불가.
 * 매 행위를 TransactionTemplate 으로 자체 commit 후 별 트랜잭션에서 audit row 를 읽고, @AfterEach 에서 truncate.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PointEarnEnversTest {

    private static final Long USER_ID = 999_001L;

    @Autowired private PointEarnRepository earnRepository;
    @Autowired private TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager em;

    @AfterEach
    void cleanup() {
        txTemplate.executeWithoutResult(s -> {
            em.createNativeQuery("DELETE FROM point_earn_aud").executeUpdate();
            em.createNativeQuery("DELETE FROM revinfo").executeUpdate();
            em.createNativeQuery("DELETE FROM point_earn WHERE user_id = ?")
                .setParameter(1, USER_ID)
                .executeUpdate();
        });
    }

    @Test
    @DisplayName("PointEarn INSERT 시 ADD 리비전이 1건 기록된다")
    void insert_creates_one_revision() {
        Long earnId = txTemplate.execute(s -> earnRepository.save(newActiveEarn(1000L)).getId());

        PointEarn snapshot = txTemplate.execute(s -> {
            AuditReader reader = AuditReaderFactory.get(em);
            var revisions = reader.getRevisions(PointEarn.class, earnId);
            assertThat(revisions).hasSize(1);
            return reader.find(PointEarn.class, earnId, revisions.get(0));
        });

        assertThat(snapshot.getInitialAmount()).isEqualTo(1000L);
        assertThat(snapshot.getRemainingAmount()).isEqualTo(1000L);
        assertThat(snapshot.getStatus()).isEqualTo(EarnStatus.ACTIVE);
    }

    @Test
    @DisplayName("PointEarn 의 cancel() 후 commit 하면 ADD + MOD 두 리비전이 쌓이고 각 시점의 status/remaining 이 보존된다")
    void cancel_persists_two_revisions_with_state_history() {
        Long earnId = txTemplate.execute(s -> earnRepository.save(newActiveEarn(1000L)).getId());

        txTemplate.executeWithoutResult(s -> {
            PointEarn earn = earnRepository.findById(earnId).orElseThrow();
            earn.cancel(LocalDateTime.now());
        });

        txTemplate.executeWithoutResult(s -> {
            AuditReader reader = AuditReaderFactory.get(em);
            var revisions = reader.getRevisions(PointEarn.class, earnId);
            assertThat(revisions).hasSize(2);

            PointEarn before = reader.find(PointEarn.class, earnId, revisions.get(0));
            PointEarn after = reader.find(PointEarn.class, earnId, revisions.get(1));

            assertThat(before.getStatus()).isEqualTo(EarnStatus.ACTIVE);
            assertThat(before.getRemainingAmount()).isEqualTo(1000L);
            assertThat(before.getCancelledAt()).isNull();

            assertThat(after.getStatus()).isEqualTo(EarnStatus.CANCELLED);
            assertThat(after.getRemainingAmount()).isZero();
            assertThat(after.getCancelledAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("useFrom() 로 remaining 만 줄여도 새 리비전이 추가되며 잔액 변화가 시점별로 보존된다")
    void use_from_creates_new_revision_with_remaining_diff() {
        Long earnId = txTemplate.execute(s -> earnRepository.save(newActiveEarn(1000L)).getId());

        txTemplate.executeWithoutResult(s -> {
            PointEarn earn = earnRepository.findById(earnId).orElseThrow();
            earn.useFrom(300L);
        });

        txTemplate.executeWithoutResult(s -> {
            AuditReader reader = AuditReaderFactory.get(em);
            var revisions = reader.getRevisions(PointEarn.class, earnId);
            assertThat(revisions).hasSize(2);
            assertThat(reader.find(PointEarn.class, earnId, revisions.get(0)).getRemainingAmount()).isEqualTo(1000L);
            assertThat(reader.find(PointEarn.class, earnId, revisions.get(1)).getRemainingAmount()).isEqualTo(700L);
        });
    }

    private PointEarn newActiveEarn(long amount) {
        return PointEarn.earn(USER_ID, amount, EarnType.SYSTEM, LocalDateTime.now().plusDays(30));
    }
}
