package com.roampass_server.issuance.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 발급 설정({@code roampass.issuance.*})
 *
 * @param callbackSecret  공급사 콜백 서명 검증용 공유 비밀키(mock-supplier와 같은 값)
 * @param callbackTimeout 발급 요청 후 콜백 대기 시간, 넘으면 실패 처리 후 자동 환불
 * @param batchSize       스케줄러 1회 최대 처리 건수
 */
@ConfigurationProperties(prefix = "roampass.issuance")
public record IssuanceProperties(String callbackSecret, Duration callbackTimeout, int batchSize) {
}
