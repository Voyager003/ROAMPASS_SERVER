package com.roampass.mock.pg.payment;

public record CancelResult(String pgTid, String cancelKey, long cancelAmount, long totalCancelledAmount,
                           MockPaymentStatus status) {
}
