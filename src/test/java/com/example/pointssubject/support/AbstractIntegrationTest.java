package com.example.pointssubject.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** 동일 캐시 키 공유 → 컨텍스트 부팅 1회 + 메소드별 트랜잭션 롤백. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest {
}
