package com.roampass_server.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Provides a real PostgreSQL container for integration tests.
 * {@link ServiceConnection} lets Spring Boot derive the datasource settings from the container,
 * so tests never need credentials. The container is shared by every test that reuses the same
 * Spring context (context caching), so it starts once per test run in practice.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Same major version as compose.yaml so local runs and tests behave the same.
    private static final String POSTGRES_IMAGE = "postgres:17";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
