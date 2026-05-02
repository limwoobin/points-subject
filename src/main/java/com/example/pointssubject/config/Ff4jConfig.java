package com.example.pointssubject.config;

import javax.sql.DataSource;
import org.ff4j.FF4j;
import org.ff4j.cache.FF4JCacheManager;
import org.ff4j.cache.FF4jCacheProxy;
import org.ff4j.cache.InMemoryCacheManager;
import org.ff4j.property.store.JdbcPropertyStore;
import org.ff4j.web.FF4jDispatcherServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FF4j 수동 빈 구성
 */
@Configuration
public class Ff4jConfig {

    public static final String CONSOLE_PATH = "/ff4j-console";

    @Bean
    public JdbcPropertyStore ff4jJdbcPropertyStore(DataSource dataSource) {
        return new JdbcPropertyStore(dataSource);
    }

    @Bean
    public FF4JCacheManager ff4jCacheManager() {
        return new InMemoryCacheManager();
    }

    /**
     * InMemoryCacheManager 는 JVM 프로세스 로컬 캐시이므로 <b>단일 인스턴스 전제</b>에서만 정합성이 보장된다.
     * 다중 인스턴스로 확장될 경우 한 인스턴스에서 Web Console 로 변경한 정책이 다른 인스턴스의 캐시에는
     * TTL 만료 전까지 반영되지 않아 stale window 가 발생한다.
     * <p>
     * 다중 인스턴스 전환 시 선택지:
     * <ul>
     *   <li>{@link FF4JCacheManager} 구현체를 Redis/Hazelcast 기반 공유 캐시로 교체 (모든 인스턴스가 같은 캐시 참조)</li>
     *   <li>InMemory 캐시 유지 + Redis Pub/Sub 등으로 invalidation 이벤트 broadcast</li>
     *   <li>트래픽 부담이 작다면 캐시 자체를 제거하고 매 요청마다 DB 직접 조회</li>
     * </ul>
     */
    @Bean
    public FF4j ff4j(JdbcPropertyStore propertyStore, FF4JCacheManager cacheManager) {
        FF4j ff4j = new FF4j();
        FF4jCacheProxy cacheProxy = new FF4jCacheProxy(ff4j.getFeatureStore(), propertyStore, cacheManager);
        ff4j.setFeatureStore(cacheProxy);
        ff4j.setPropertiesStore(cacheProxy);
        return ff4j;
    }

    @Bean
    public ServletRegistrationBean<FF4jDispatcherServlet> ff4jConsoleServlet(FF4j ff4j) {
        FF4jDispatcherServlet servlet = new FF4jDispatcherServlet();
        servlet.setFf4j(ff4j);
        servlet.setAskConfirmationBeforeDeleting(true);
        servlet.setAskConfirmationBeforeToggling(true);
        ServletRegistrationBean<FF4jDispatcherServlet> registration =
            new ServletRegistrationBean<>(servlet, CONSOLE_PATH + "/*");
        registration.setName("ff4jConsole");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
