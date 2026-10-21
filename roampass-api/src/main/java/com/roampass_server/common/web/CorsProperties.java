package com.roampass_server.common.web;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 설정({@code roampass.cors.*})
 *
 * @param allowedOrigins 브라우저 호출을 허용할 프론트 주소 목록(로컬 기본 http://localhost:3000, 배포 시 환경변수로 교체)
 */
@ConfigurationProperties(prefix = "roampass.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
