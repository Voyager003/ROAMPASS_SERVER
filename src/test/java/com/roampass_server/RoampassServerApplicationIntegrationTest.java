package com.roampass_server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@DisplayName("애플리케이션 기동")
class RoampassServerApplicationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("test 프로필로 기동하면")
    class WhenStartedWithTestProfile {

        @Test
        @DisplayName("Testcontainers의 PostgreSQL에 연결된다")
        void connects_to_testcontainers_postgresql() {
            // when
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);

            // then
            assertThat(version).startsWith("PostgreSQL 17");
        }
    }
}
