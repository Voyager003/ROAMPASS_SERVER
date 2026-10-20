package com.roampass_server.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.roampass_server.common.domain.UserId;

/**
 * {@link UserId}를 선언한 컨트롤러 메서드에 값 주입
 * 헤더는 그런 메서드에서만 필수라서 공개 API(예: 요금제 목록)는 헤더 없이 호출 가능
 */
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(UserId.class);
    }

    @Override
    public UserId resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String value = webRequest.getHeader(AuthHeaders.USER_ID);
        if (value == null) {
            throw new MissingAuthHeaderException("MISSING_USER_ID", AuthHeaders.USER_ID);
        }
        return UserId.of(value);
    }
}
