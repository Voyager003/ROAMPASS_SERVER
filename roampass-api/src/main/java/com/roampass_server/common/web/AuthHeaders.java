package com.roampass_server.common.web;

/**
 * 호출자 식별에 쓰는 헤더 이름. 과제가 실제 인증 시스템 대신 헤더 수준의 식별을
 * 명시적으로 허용
 */
public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String ADMIN_ID = "X-Admin-Id";

    private AuthHeaders() {
    }
}
