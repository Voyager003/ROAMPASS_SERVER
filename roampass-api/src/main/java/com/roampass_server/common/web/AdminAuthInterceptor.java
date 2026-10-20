package com.roampass_server.common.web;

import org.springframework.web.servlet.HandlerInterceptor;

import com.roampass_server.common.domain.AdminId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Requires a well-formed X-Admin-Id on every /admin/** request, even when the controller
 * method does not declare an {@link AdminId} parameter (fail-closed for new admin endpoints).
 */
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Let CORS preflight requests through; browsers never attach custom headers to them.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String value = request.getHeader(AuthHeaders.ADMIN_ID);
        if (value == null) {
            throw new MissingAuthHeaderException("MISSING_ADMIN_ID", AuthHeaders.ADMIN_ID);
        }
        AdminId.of(value); // validates the format; throws InvalidValueException (400) if malformed
        return true;
    }
}
