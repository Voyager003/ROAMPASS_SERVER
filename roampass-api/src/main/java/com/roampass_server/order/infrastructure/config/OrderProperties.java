package com.roampass_server.order.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 주문 관련 설정값({@code roampass.order.*})
 *
 * @param paymentWindow 주문 생성 후 결제를 기다리는 시간
 * @param expiration    결제 대기 만료 스케줄러 설정
 */
@ConfigurationProperties(prefix = "roampass.order")
public record OrderProperties(Duration paymentWindow, Expiration expiration) {

    /**
     * @param batchSize 한 번 실행에 만료 처리하는 최대 건수
     */
    public record Expiration(int batchSize) {
    }
}
