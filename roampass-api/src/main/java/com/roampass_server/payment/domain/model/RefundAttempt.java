package com.roampass_server.payment.domain.model;

import java.time.Instant;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;

/**
 * One refund request for a payment (aggregate root). Retries of the PG cancel are tracked on this single record
 * (attempt count, next retry time, last error) instead of one record per try.
 * Retries are bounded by {@link RetryPolicy}: after the last allowed failure the request GAVE_UP and an operator takes over.
 * A manual refund after that is a new RefundAttempt, so the automatic history stays visible.
 * <p>
 * No natural key exists (an order may have an automatic refund and later a manual one), so equality is
 * object identity; persistence identifies records by id.
 */
public final class RefundAttempt {

    private static final int MAX_ERROR_LENGTH = 500;

    private final Long id;
    private final IdempotencyKey paymentKey;
    private final OrderNo orderNo;
    private final Money amount;
    private final RefundStatus status;
    private final int attemptCount;
    private final Instant nextRetryAt;
    private final String lastError;
    private final Instant createdAt;
    private final Instant completedAt;

    private RefundAttempt(Long id, IdempotencyKey paymentKey, OrderNo orderNo, Money amount, RefundStatus status,
                          int attemptCount, Instant nextRetryAt, String lastError, Instant createdAt, Instant completedAt) {
        this.id = id;
        this.paymentKey = paymentKey;
        this.orderNo = orderNo;
        this.amount = amount;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    /** Creates a refund request that is due immediately. */
    public static RefundAttempt start(IdempotencyKey paymentKey, OrderNo orderNo, Money amount, Instant now) {
        if (paymentKey == null || orderNo == null || amount == null || now == null) {
            throw new InvalidValueException("INVALID_REFUND", "Payment key, order number, amount and time are required");
        }
        if (amount.amount() == 0) {
            throw new InvalidValueException("INVALID_REFUND", "Refund amount must be greater than zero");
        }
        return new RefundAttempt(null, paymentKey, orderNo, amount, RefundStatus.PENDING, 0, now, null, now, null);
    }

    /** Whether the scheduler should try the PG cancel now. */
    public boolean isDue(Instant now) {
        return status == RefundStatus.PENDING && !now.isBefore(nextRetryAt);
    }

    public RefundAttempt recordSuccess(Instant now) {
        requirePending(RefundStatus.SUCCEEDED);
        return new RefundAttempt(id, paymentKey, orderNo, amount, RefundStatus.SUCCEEDED, attemptCount + 1,
                null, lastError, createdAt, now);
    }

    /**
     * Records a failed (or timed-out) PG cancel. Either schedules the next attempt with backoff
     * or gives up when the policy's attempt limit is reached.
     */
    public RefundAttempt recordFailure(String error, Instant now, RetryPolicy policy) {
        requirePending(RefundStatus.GAVE_UP);
        int failed = attemptCount + 1;
        String trimmed = truncate(error);
        if (policy.isExhausted(failed)) {
            return new RefundAttempt(id, paymentKey, orderNo, amount, RefundStatus.GAVE_UP, failed,
                    null, trimmed, createdAt, now);
        }
        return new RefundAttempt(id, paymentKey, orderNo, amount, RefundStatus.PENDING, failed,
                now.plus(policy.delayAfter(failed)), trimmed, createdAt, null);
    }

    private void requirePending(RefundStatus target) {
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateTransitionException("RefundAttempt", status, target);
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public Long id() {
        return id;
    }

    public IdempotencyKey paymentKey() {
        return paymentKey;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public Money amount() {
        return amount;
    }

    public RefundStatus status() {
        return status;
    }

    /** Number of PG cancel attempts made so far (successful or not). */
    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public String lastError() {
        return lastError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
