package com.roampass_server.order.domain.model;

import com.roampass_server.common.domain.DomainException;

/**
 * SIM이 이미 설치·개통된 발급 완료 주문에 대한 환불 요청
 */
public class OrderNotRefundableException extends DomainException {

    public OrderNotRefundableException() {
        super("ORDER_NOT_REFUNDABLE", "An installed or activated SIM cannot be refunded");
    }
}
