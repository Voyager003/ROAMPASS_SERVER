package com.roampass.mock.pg.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "mock-pg.webhook.url=") // 웹훅 발송 끔
@DisplayName("장애 주입 설정 API")
class FaultConfigApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @AfterEach
    void reset() {
        assertThat(mvc.post().uri("/mock/pg/config/reset")).hasStatusOk();
    }

    @Test
    @DisplayName("기본 설정은 장애 없음")
    void default_is_no_fault() {
        // when
        var result = mvc.get().uri("/mock/pg/config");

        // then
        assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"approveFailureRate":0.0,"responseDelayMs":0,"webhookDuplicates":0,"webhookDrop":false,
                 "webhookAmountDelta":0,"cancelFailureRate":0.0}
                """);
    }

    @Test
    @DisplayName("실행 중에 승인 실패율을 1로 바꾸면 이후 승인은 FAILED")
    void changes_at_runtime() {
        // given
        assertThat(mvc.put().uri("/mock/pg/config").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"approveFailureRate":1.0}
                        """)).hasStatusOk().bodyJson().extractingPath("$.approveFailureRate").isEqualTo(1.0);

        // when
        var result = mvc.post().uri("/mock/pg/approve").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idempotencyKey":"K-fault-1","orderNo":"ORD-20261020-AAAA1111","amount":9900,"currency":"KRW"}
                        """);

        // then
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("FAILED");
    }

    @Test
    @DisplayName("범위를 벗어난 값이면 400 INVALID_FAULT_CONFIG이고 설정은 바뀌지 않는다")
    void rejects_invalid() {
        // when
        var result = mvc.put().uri("/mock/pg/config").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"approveFailureRate":2.0}
                        """);

        // then
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_FAULT_CONFIG");
        assertThat(mvc.get().uri("/mock/pg/config"))
                .bodyJson().extractingPath("$.approveFailureRate").isEqualTo(0.0);
    }
}
