package com.roampass_server.payment.application;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.PaymentStatus;

/**
 * 결제 요청 결과. paymentStatus가 REQUESTED면 PG 결과를 아직 모름(웹훅·상태 조회로 확정 예정)
 */
public record PayResult(OrderNo orderNo, IdempotencyKey idempotencyKey, PaymentStatus paymentStatus,
                        OrderStatus orderStatus) {
}
