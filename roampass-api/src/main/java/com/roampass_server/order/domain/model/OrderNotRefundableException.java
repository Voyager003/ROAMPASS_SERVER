package com.roampass_server.order.domain.model;

import com.roampass_server.common.domain.DomainException;

/**
 * Refund was requested for an issued order whose SIM is already installed or activated.
 */
public class OrderNotRefundableException extends DomainException {

    public OrderNotRefundableException() {
        super("ORDER_NOT_REFUNDABLE", "An installed or activated SIM cannot be refunded");
    }
}
