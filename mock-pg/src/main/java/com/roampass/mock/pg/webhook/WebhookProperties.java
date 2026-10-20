package com.roampass.mock.pg.webhook;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹훅 설정({@code mock-pg.webhook.*})
 *
 * @param url    수신 주소, 비어 있으면 발송 안 함
 * @param secret 서명용 공유 비밀키
 * @param delay  승인 후 발송까지 지연
 */
@ConfigurationProperties(prefix = "mock-pg.webhook")
public record WebhookProperties(String url, String secret, Duration delay) {

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
