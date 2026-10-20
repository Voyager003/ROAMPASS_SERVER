package com.roampass_server.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.roampass_server.common.domain.AdminId;

/**
 * Injects {@link AdminId} into controller methods that declare it.
 * Presence of the header on /admin/** is already enforced by {@link AdminAuthInterceptor};
 * the null check here covers admin-id parameters used outside that path.
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
