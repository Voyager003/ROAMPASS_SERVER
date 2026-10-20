package com.roampass_server.payment.application;

/**
 * 웹훅 처리 결과. DUPLICATE는 같은 이벤트 재수신(기록·반영 모두 생략)
 */
public enum WebhookResult {
    PROCESSED,
    DUPLICATE,
    ALREADY_APPLIED,
    AMOUNT_MISMATCH,
    PAYMENT_NOT_FOUND,
    IGNORED
}
