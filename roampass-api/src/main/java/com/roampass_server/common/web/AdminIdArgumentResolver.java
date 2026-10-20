package com.roampass_server.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.roampass_server.common.domain.AdminId;

/**
 * {@link AdminId}를 선언한 컨트롤러 메서드에 값 주입
 * /admin/** 경로의 헤더 존재 여부는 {@link AdminAuthInterceptor}가 먼저 확인,
 * 여기의 null 검사는 그 경로 밖에서 AdminId 파라미터를 쓰는 경우 대비
 */
public class AdminIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AdminId.class);
    }

    @Override
    public AdminId resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String value = webRequest.getHeader(AuthHeaders.ADMIN_ID);
        if (value == null) {
            throw new MissingAuthHeaderException("MISSING_ADMIN_ID", AuthHeaders.ADMIN_ID);
        }
        return AdminId.of(value);
    }
}
