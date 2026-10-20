package com.roampass.mock.supplier.issuance;

/**
 * 발급 요청. 같은 멱등 키 재요청은 같은 접수 번호 반환(접수 후 응답 유실 대비)
 */
public record IssueCommand(String idempotencyKey, String orderNo) {
}
