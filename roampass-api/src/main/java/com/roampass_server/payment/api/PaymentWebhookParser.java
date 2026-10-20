package com.roampass_server.payment.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.payment.application.PaymentWebhookCommand;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * PG 웹훅 본문(JSON) → 명령. 서명 검증 뒤에만 호출
 */
class PaymentWebhookParser {

    private final JsonMapper jsonMapper;

    PaymentWebhookParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    PaymentWebhookCommand parse(String rawBody) {
        try {
            Body body = jsonMapper.readValue(rawBody, Body.class);
            return new PaymentWebhookCommand(body.eventId(), body.eventType(), body.idempotencyKey(), body.pgTid(),
                    body.amount(), body.currency());
        } catch (JacksonException e) {
            throw new InvalidValueException("INVALID_WEBHOOK", "Webhook body is not valid JSON");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(String eventId, String eventType, String idempotencyKey, String pgTid, long amount,
                String currency) {
    }
}
