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

    /** InMemory 캐시는 단일 인스턴스 전제. 다중 인스턴스 시 Redis/Hazelcast 공유 캐시로 교체 필요. */
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
