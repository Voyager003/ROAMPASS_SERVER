package com.roampass_server.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.payment.application.PaymentWebhookCommand;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("PG 웹훅 본문 해석")
class PaymentWebhookParserTest {

    private final PaymentWebhookParser parser = new PaymentWebhookParser(JsonMapper.builder().build());

    @Test
    @DisplayName("mock-pg 웹훅 본문을 명령으로 바꾼다 (모르는 필드는 무시)")
    void parses() {
        // when
        PaymentWebhookCommand command = parser.parse("""
                {"eventId":"evt_1","eventType":"PAYMENT_APPROVED","idempotencyKey":"ORD-20261020-AAAA1111-1",
                 "pgTid":"pg_1","orderNo":"ORD-20261020-AAAA1111","amount":9900,"currency":"KRW",
                 "occurredAt":"2026-10-20T03:00:01Z"}
                """);

        // then
        assertThat(command).isEqualTo(new PaymentWebhookCommand("evt_1", "PAYMENT_APPROVED",
                "ORD-20261020-AAAA1111-1", "pg_1", 9_900, "KRW"));
    }

    @Test
    @DisplayName("JSON이 아니면 400 INVALID_WEBHOOK")
    void rejects_malformed() {
        // when // then
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOfSatisfying(InvalidValueException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_WEBHOOK"));
    }
}
