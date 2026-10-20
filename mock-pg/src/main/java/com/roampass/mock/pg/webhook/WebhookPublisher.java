package com.roampass.mock.pg.webhook;

/**
 * 웹훅 발행 포트. 결제 처리 테스트에서 발행 내역만 수집하도록 분리
 */
@FunctionalInterface
public interface WebhookPublisher {

    void publish(WebhookEvent event);
}
