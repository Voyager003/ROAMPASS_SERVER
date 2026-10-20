package com.roampass.mock.pg.webhook;

import java.time.Instant;

/**
 * 웹훅 본문. eventId는 이벤트마다 고유, 같은 이벤트를 다시 보내도 동일(수신 측 중복 판정 키)
 */
public record WebhookEvent(String eventId, WebhookEventType eventType, String idempotencyKey, String pgTid,
                           String orderNo, long amount, String currency, Instant occurredAt) {
}
