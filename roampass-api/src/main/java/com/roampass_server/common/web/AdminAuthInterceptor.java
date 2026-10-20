package com.roampass_server.common.web;

import org.springframework.web.servlet.HandlerInterceptor;

import com.roampass_server.common.domain.AdminId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 /admin/** 요청에 형식이 올바른 X-Admin-Id 필수
 * 컨트롤러 메서드에 {@link AdminId} 파라미터가 없어도 적용(새 어드민 API도 기본 차단)
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS preflight 요청은 통과. 브라우저는 preflight에 사용자 정의 헤더를 붙이지 않음
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String value = request.getHeader(AuthHeaders.ADMIN_ID);
        if (value == null) {
            throw new MissingAuthHeaderException("MISSING_ADMIN_ID", AuthHeaders.ADMIN_ID);
        }
        AdminId.of(value); // 형식 검증, 형식이 틀리면 InvalidValueException(400)
        return true;
    }
}
