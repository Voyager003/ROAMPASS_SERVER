package com.roampass_server.payment.domain.model;

import com.roampass_server.common.domain.DomainException;

/**
 * An approval notice carried an amount different from the payment amount.
 * The payment must not be completed; the caller records it as an anomaly.
 */
public class PaymentAmountMismatchException extends DomainException {

    public PaymentAmountMismatchException() {
        super("PAYMENT_AMOUNT_MISMATCH", "Approved amount does not match the payment amount");
    }
}
