package com.roampass_server.payment.domain.model;

/**
 * State of one refund request.
 */
public enum RefundStatus {
    /** Waiting for the next PG cancel attempt (at nextRetryAt). */
    PENDING,
    /** PG cancel confirmed. Terminal. */
    SUCCEEDED,
    /** Retries exhausted; an operator must look at the order. Terminal — a manual refund starts a new request. */
    GAVE_UP
}
