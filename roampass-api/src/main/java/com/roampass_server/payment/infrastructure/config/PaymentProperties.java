package com.roampass_server.payment.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 연동 설정({@code roampass.pg.*})
 *
 * @param baseUrl        PG(mock-pg) 주소
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 대기 타임아웃, 넘으면 결과 UNKNOWN(결제는 REQUESTED로 남고 웹훅·상태 조회로 확정)
 * @param webhookSecret  PG 웹훅 서명 검증용 공유 비밀키(mock-pg와 같은 값)
 * @param reconciliation 응답 유실 결제 정리 스케줄러 설정
 */
@ConfigurationProperties(prefix = "roampass.pg")
public record PaymentProperties(String baseUrl, Duration connectTimeout, Duration readTimeout,
                                String webhookSecret, Reconciliation reconciliation) {

    /**
     * @param staleAfter 요청 후 이 시간이 지난 REQUESTED만 조회(읽기 타임아웃보다 길게)
     * @param batchSize  한 번 실행에 조회하는 최대 건수
     * @param fixedDelay 실행 간격, 이전 실행 종료 기준
     */
    public record Reconciliation(Duration staleAfter, int batchSize, Duration fixedDelay) {
    }
}
