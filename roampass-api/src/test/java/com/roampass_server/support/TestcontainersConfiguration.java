package com.roampass_server.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트용 실제 PostgreSQL 컨테이너 제공
 * {@link ServiceConnection} 덕분에 Spring Boot가 컨테이너에서 데이터소스 설정을 가져오므로
 * 테스트에 접속 정보 불필요. 같은 Spring 컨텍스트를 재사용하는 테스트가 컨테이너를
 * 함께 쓰므로(컨텍스트 캐싱) 실제로는 테스트 실행당 한 번만 기동
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // 로컬 실행과 테스트가 같은 동작을 하도록 compose.yaml과 같은 메이저 버전 사용
    private static final String POSTGRES_IMAGE = "postgres:17";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
