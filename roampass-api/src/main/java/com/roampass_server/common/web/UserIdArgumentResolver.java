package com.roampass_server.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.roampass_server.common.domain.UserId;

/**
 * Injects {@link UserId} into controller methods that declare it.
 * The header is required only for such methods, so public endpoints (e.g. plan listing) need no header.
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
