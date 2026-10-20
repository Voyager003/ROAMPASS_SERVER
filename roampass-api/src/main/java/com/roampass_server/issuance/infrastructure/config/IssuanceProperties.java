package com.roampass_server.issuance.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 발급 설정({@code roampass.issuance.*})
 *
 * @param supplierBaseUrl    공급사(mock-supplier) 주소
 * @param connectTimeout     공급사 연결 타임아웃
 * @param readTimeout        공급사 응답 대기 타임아웃, 넘으면 접수 여부를 모르는 것으로 보고 같은 멱등 키로 재전송
 * @param callbackSecret     공급사 콜백 서명 검증용 공유 비밀키(mock-supplier와 같은 값)
 * @param callbackTimeout    발급 요청 후 콜백 대기 시간, 넘으면 실패 처리 후 자동 환불
 * @param batchSize          스케줄러 1회 최대 처리 건수
 * @param dispatchFixedDelay 발급 요청 스케줄러 간격
 * @param timeoutFixedDelay  콜백 대기 초과 검사 간격
 */
@ConfigurationProperties(prefix = "roampass.issuance")
public record IssuanceProperties(String supplierBaseUrl, Duration connectTimeout, Duration readTimeout,
                                 String callbackSecret, Duration callbackTimeout, int batchSize,
                                 Duration dispatchFixedDelay, Duration timeoutFixedDelay) {
}
