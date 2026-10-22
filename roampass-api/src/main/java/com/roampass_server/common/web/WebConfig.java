package com.roampass_server.common.web;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UserIdArgumentResolver());
        resolvers.add(new AdminIdArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthInterceptor()).addPathPatterns("/admin/**");
    }

    /**
     * 프론트(별도 도메인)에서 호출하는 사용자·어드민 API만 허용
     * 인증 헤더(X-User-Id·X-Admin-Id)를 preflight 허용 헤더에 포함, 주문 생성 응답의 Location 노출
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = corsProperties.allowedOrigins().toArray(String[]::new);
        String[] methods = {"GET", "POST", "PUT", "OPTIONS"};
        String[] headers = {HttpHeaders.CONTENT_TYPE, AuthHeaders.USER_ID, AuthHeaders.ADMIN_ID};
        for (String pattern : new String[]{"/api/**", "/admin/**"}) {
            registry.addMapping(pattern)
                    .allowedOrigins(origins)
                    .allowedMethods(methods)
                    .allowedHeaders(headers)
                    .exposedHeaders(HttpHeaders.LOCATION, RequestTracingFilter.REQUEST_ID_HEADER)
                    .maxAge(3600);
        }
    }
}
