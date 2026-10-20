package com.roampass_server.issuance.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 설치 추적 폴링 설정({@code roampass.tracking.*})
 *
 * @param lookaheadDays 여행 시작일 며칠 전부터 조회할지
 * @param batchSize     1회 최대 조회 건수
 * @param fixedDelay    실행 간격, 이전 실행 종료 기준
 */
@ConfigurationProperties(prefix = "roampass.tracking")
public record TrackingProperties(int lookaheadDays, int batchSize, Duration fixedDelay) {
}
