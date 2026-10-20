package com.roampass_server.payment.application;

/**
 * PG 웹훅 본문(검증 전 원시 값). 형식 검증은 처리 시점
 */
public record PaymentWebhookCommand(String eventId, String eventType, String idempotencyKey, String pgTid,
                                    long amount, String currency) {
}
