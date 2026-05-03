package com.example.pointssubject.policy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ff4j.property.PropertyLong;
import org.ff4j.property.store.JdbcPropertyStore;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 기동 시 yml seed → FF4j. 이미 있으면 skip (운영 콘솔 변경값을 재기동으로 회귀시키지 않음). */
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
