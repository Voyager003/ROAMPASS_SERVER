package com.roampass.mock.pg;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("mock-pg 기동")
class MockPgApplicationIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Nested
    @DisplayName("애플리케이션이 뜨면")
    class WhenStarted {

        @Test
        @DisplayName("헬스 체크가 UP을 반환한다")
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
    }
}
