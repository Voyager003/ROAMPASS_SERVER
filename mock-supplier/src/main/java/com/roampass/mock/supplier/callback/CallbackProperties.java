package com.roampass.mock.supplier.callback;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 콜백 설정({@code mock-supplier.callback.*})
 *
 * @param url    수신 주소, 비어 있으면 발송 안 함
 * @param secret 서명용 공유 비밀키
 * @param delay  접수 후 콜백까지 지연("몇 초 뒤 콜백")
 */
@ConfigurationProperties(prefix = "mock-supplier.callback")
public record CallbackProperties(String url, String secret, Duration delay) {

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
