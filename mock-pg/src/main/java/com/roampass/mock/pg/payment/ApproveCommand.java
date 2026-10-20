package com.roampass.mock.pg.payment;

/**
 * 승인 요청. 같은 멱등 키 재요청은 처음 결과를 그대로 반환
 */
public record ApproveCommand(String idempotencyKey, String orderNo, long amount, String currency) {
}
