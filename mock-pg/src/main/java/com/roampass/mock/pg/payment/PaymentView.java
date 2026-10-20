package com.roampass.mock.pg.payment;

import java.time.Instant;

/**
 * 결제 한 건의 특정 시점 스냅샷, 승인·상태 조회 응답으로 그대로 사용
 * FAILED면 pgTid·approvedAt 없음
 */
public record PaymentView(String idempotencyKey, String pgTid, String orderNo, long amount, String currency,
                          MockPaymentStatus status, long cancelledAmount, Instant approvedAt) {
}
