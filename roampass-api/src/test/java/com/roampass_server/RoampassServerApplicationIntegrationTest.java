package com.roampass_server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@DisplayName("애플리케이션 기동")
class RoampassServerApplicationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private Clock clock;

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

        @Test
        @DisplayName("헬스 체크가 UP을 반환한다 (DB 연결 포함)")
        void health_endpoint_reports_up() {
            // when
            var result = mvc.get().uri("/actuator/health");

            // then
            assertThat(result)
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.status")
                    .isEqualTo("UP");
        }

        @Test
        @DisplayName("현재 시각의 출처인 Clock 빈은 UTC다")
        void clock_bean_is_utc() {
            // when & then
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        }
    }
}
