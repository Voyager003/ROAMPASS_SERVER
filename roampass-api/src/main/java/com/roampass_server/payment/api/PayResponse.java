package com.roampass_server.payment.api;

import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.application.PayResult;
import com.roampass_server.payment.domain.model.PaymentStatus;

/**
 * 결제 요청 응답. paymentStatus가 REQUESTED면 결과 확정 전(주문 조회로 확인)
 */
public record PayResponse(String orderNo, String idempotencyKey, PaymentStatus paymentStatus,
                          OrderStatus orderStatus) {

    static PayResponse from(PayResult result) {
        return new PayResponse(result.orderNo().value(), result.idempotencyKey().value(), result.paymentStatus(),
                result.orderStatus());
    }
}
