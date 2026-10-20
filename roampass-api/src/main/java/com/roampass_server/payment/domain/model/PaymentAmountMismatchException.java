package com.roampass_server.payment.domain.model;

import com.roampass_server.common.domain.exception.DomainException;

/**
 * 승인 통지의 금액이 결제 금액과 불일치
 * 결제 완료 처리 금지, 호출한 쪽이 이상 건으로 기록
 */
public class PaymentAmountMismatchException extends DomainException {

    public PaymentAmountMismatchException() {
        super("PAYMENT_AMOUNT_MISMATCH", "Approved amount does not match the payment amount");
    }
}
