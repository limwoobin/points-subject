package com.example.pointssubject.policy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ff4j.property.PropertyLong;
import org.ff4j.property.store.JdbcPropertyStore;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 앱 기동 시 {@link PolicyKey} 항목을 yml/env 값으로 FF4j 에 시드.
 * 멱등 (이미 있으면 skip — 운영 콘솔에서 변경된 값이 재기동으로 회귀되지 않게).
 * 키 누락 시 {@code getRequiredProperty} 가 예외를 던져 부팅 실패 (silent fallback 금지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointPolicyBootstrapper {

    private final JdbcPropertyStore propertyStore;
    private final Environment environment;

    @PostConstruct
    void init() {
        propertyStore.createSchema();
        for (PolicyKey k : PolicyKey.values()) {
            seed(k);
        }
    }

    private void seed(PolicyKey k) {
        if (propertyStore.existProperty(k.getKey())) {
            return;
        }

        long seedValue = environment.getRequiredProperty(k.getKey(), Long.class);
        PropertyLong property = new PropertyLong(k.getKey(), seedValue);
        property.setDescription(k.getDescription());
        propertyStore.createProperty(property);
        log.info("FF4j seed: {} = {}", k.getKey(), seedValue);
    }
}
