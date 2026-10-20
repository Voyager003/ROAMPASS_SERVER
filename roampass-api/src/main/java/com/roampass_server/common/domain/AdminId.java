package com.roampass_server.common.domain;

/**
 * 어드민 요청을 보낸 운영자 식별자(X-Admin-Id 헤더 값)
 * 환불·재발급 같은 수동 조치의 주체로 기록
 */
public record AdminId(String value) {

    public AdminId {
        IdentifierFormat.require(value, "INVALID_ADMIN_ID", "AdminId");
    }

    public static AdminId of(String value) {
        return new AdminId(value);
    }
}
