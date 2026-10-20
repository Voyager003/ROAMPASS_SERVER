package com.roampass_server.payment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 연동 설정({@code roampass.pg.*})
 *
 * @param webhookSecret PG 웹훅 서명 검증용 공유 비밀키(mock-pg와 같은 값)
 */
@ConfigurationProperties(prefix = "roampass.pg")
public record PaymentProperties(String webhookSecret) {
}
