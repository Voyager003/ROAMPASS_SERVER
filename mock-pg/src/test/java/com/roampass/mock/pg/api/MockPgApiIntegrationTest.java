package com.roampass.mock.pg.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "mock-pg.webhook.url=") // 웹훅 발송 끔
@DisplayName("mock-pg HTTP API")
class MockPgApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    private String approve(String key, long amount) {
        var result = mvc.post().uri("/mock/pg/approve").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idempotencyKey":"%s","orderNo":"ORD-20261020-AAAA1111","amount":%d,"currency":"KRW"}
                        """.formatted(key, amount))
                .exchange();
        assertThat(result).hasStatusOk();
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.pgTid");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("POST /mock/pg/approve")
    class Approve {

        @Test
        @DisplayName("승인 결과와 PG 거래 ID를 돌려준다")
        void approves() {
            // when
            var result = mvc.post().uri("/mock/pg/approve").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"idempotencyKey":"K-approve-1","orderNo":"ORD-20261020-AAAA1111","amount":9900,"currency":"KRW"}
                            """);

            // then
            assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                    {"idempotencyKey":"K-approve-1","orderNo":"ORD-20261020-AAAA1111","status":"APPROVED",
                     "amount":9900,"currency":"KRW","cancelledAmount":0}
                    """);
        }

        @Test
        @DisplayName("필수 값이 없거나 금액이 0 이하면 400 INVALID_REQUEST")
        void validates() {
            // when
            var result = mvc.post().uri("/mock/pg/approve").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"idempotencyKey":"K-approve-2","orderNo":"ORD-20261020-AAAA1111","amount":0,"currency":"KRW"}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }

        @Test
        @DisplayName("같은 멱등 키를 다른 금액으로 재사용하면 409 IDEMPOTENCY_KEY_REUSED")
        void reused_key() {
            // given
            approve("K-approve-3", 9_900);

            // when
            var result = mvc.post().uri("/mock/pg/approve").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"idempotencyKey":"K-approve-3","orderNo":"ORD-20261020-AAAA1111","amount":100,"currency":"KRW"}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        }
    }

    @Nested
    @DisplayName("POST /mock/pg/cancel")
    class Cancel {

        @Test
        @DisplayName("부분 취소 결과를 돌려준다")
        void cancels() {
            // given
            String tid = approve("K-cancel-1", 9_900);

            // when
            var result = mvc.post().uri("/mock/pg/cancel").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"pgTid":"%s","cancelKey":"C-1","amount":4000}
                            """.formatted(tid));

            // then
            assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                    {"cancelKey":"C-1","cancelAmount":4000,"totalCancelledAmount":4000,"status":"PARTIALLY_CANCELLED"}
                    """);
        }

        @Test
        @DisplayName("모르는 거래 ID면 404 PAYMENT_NOT_FOUND")
        void unknown_tid() {
            // when
            var result = mvc.post().uri("/mock/pg/cancel").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"pgTid":"pg_nope","cancelKey":"C-1","amount":1}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("GET /mock/pg/payments/{idempotencyKey}")
    class Find {

        @Test
        @DisplayName("승인된 결제의 현재 상태를 돌려준다")
        void found() {
            // given
            String tid = approve("K-find-1", 9_900);

            // when
            var result = mvc.get().uri("/mock/pg/payments/K-find-1");

            // then
            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.pgTid").isEqualTo(tid);
        }

        @Test
        @DisplayName("받은 적 없는 키면 404 PAYMENT_NOT_FOUND")
        void not_found() {
            // when
            var result = mvc.get().uri("/mock/pg/payments/K-none");

            // then
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("PAYMENT_NOT_FOUND");
        }
    }
}
