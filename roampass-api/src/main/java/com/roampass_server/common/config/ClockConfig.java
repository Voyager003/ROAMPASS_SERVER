package com.roampass_server.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "현재 시각"의 유일한 출처. 서비스는 {@code Instant.now(clock)}으로 얻은 시각을 도메인 객체에 전달,
 * 테스트는 이 빈을 고정 시계로 교체
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
