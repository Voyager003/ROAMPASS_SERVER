package com.roampass_server.common.web;

/**
 * Header names used to identify callers. The assignment explicitly allows header-level
 * identification instead of a real authentication system.
 */
public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String ADMIN_ID = "X-Admin-Id";

    private AuthHeaders() {
    }
}
