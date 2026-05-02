package com.example.pointssubject.config;

import com.example.pointssubject.domain.entity.BaseEntity;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** 인증 도입 시 람다를 {@code SecurityContextHolder} 조회로 교체. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(BaseEntity.SYSTEM_AUDITOR);
    }
}
