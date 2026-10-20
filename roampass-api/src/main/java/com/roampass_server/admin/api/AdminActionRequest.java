package com.roampass_server.admin.api;

/**
 * 운영자 조치 요청. 사유 필수 검증은 유스케이스(ADMIN_REASON_REQUIRED)
 */
public record AdminActionRequest(String reason) {
}
