package com.roampass_server.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Testcontainers PostgreSQL을 쓰는 전체 컨텍스트 통합 테스트 표시
 * 모든 통합 테스트가 같은 애너테이션 조합을 쓰면 Spring이 테스트 클래스 사이에서 컨텍스트 하나
 * (그리고 컨테이너 하나)를 재사용. HTTP 수준 테스트도 같은 컨텍스트를 쓰도록 MockMvc 포함
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
