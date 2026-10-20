package com.roampass_server.payment.domain.model;

import java.time.Instant;
import java.util.Objects;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;

/**
 * One payment attempt for an order (aggregate root). Immutable; identity is the idempotency key.
 * <ul>
 *   <li>Approval is idempotent: the same approval (same PG transaction id) arriving again changes nothing,
 *       so an approval response plus one or more webhooks are applied once.</li>
 *   <li>An approval whose amount differs from the payment amount is rejected.</li>
 *   <li>Cancels accumulate and can never exceed the approved amount.</li>
 * </ul>
 */
public final class Payment {

    private final Long id;
    private final OrderNo orderNo;
    private final IdempotencyKey idempotencyKey;
    private final Money amount;
    private final PaymentStatus status;
    private final PgTid pgTid;
    private final Money cancelledAmount;
    private final String failureReason;
    private final Instant requestedAt;
    private final Instant approvedAt;

    private Payment(Long id, OrderNo orderNo, IdempotencyKey idempotencyKey, Money amount, PaymentStatus status,
                    PgTid pgTid, Money cancelledAmount, String failureReason, Instant requestedAt, Instant approvedAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = status;
        this.pgTid = pgTid;
        this.cancelledAmount = cancelledAmount;
        this.failureReason = failureReason;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
    }

    /**
     * Starts a new payment attempt.
     *
     * @param attempt 1 for the first attempt of the order, 2 after a failed first attempt, ...
     */
    public static Payment request(OrderNo orderNo, int attempt, Money amount, Instant now) {
        if (orderNo == null || amount == null || now == null) {
            throw new InvalidValueException("INVALID_PAYMENT", "Order number, amount and time are required");
        }
        if (amount.amount() == 0) {
            throw new InvalidValueException("INVALID_PAYMENT", "Payment amount must be greater than zero");
        }
        return new Payment(null, orderNo, IdempotencyKey.of(orderNo, attempt), amount, PaymentStatus.REQUESTED,
                null, Money.zero(amount.currency()), null, now, null);
    }

    /**
     * Applies an approval notice (from the PG's response or its webhook).
     * Returns {@code this} unchanged when the same approval was already applied.
     */
    public Payment approve(PgTid tid, Money approvedAmount, Instant now) {
        if (tid == null || approvedAmount == null) {
            throw new InvalidValueException("INVALID_PAYMENT", "PG transaction id and approved amount are required");
        }
        if (!amount.equals(approvedAmount)) {
            throw new PaymentAmountMismatchException();
        }
        if (pgTid != null) {
            if (pgTid.equals(tid)) {
                return this; // duplicate notice of the approval already applied
            }
            throw new IllegalStateTransitionException("Payment", status, PaymentStatus.APPROVED + " (different PG transaction id)");
        }
        requireTransition(PaymentStatus.APPROVED);
        return new Payment(id, orderNo, idempotencyKey, amount, PaymentStatus.APPROVED, tid, cancelledAmount,
                null, requestedAt, now);
    }

    /**
     * Applies a failure notice. A repeated failure notice changes nothing; an approved payment is never flipped.
     */
    public Payment fail(String reason, Instant now) {
        if (status == PaymentStatus.FAILED) {
            return this;
        }
        requireTransition(PaymentStatus.FAILED);
        return new Payment(id, orderNo, idempotencyKey, amount, PaymentStatus.FAILED, null, cancelledAmount,
                reason, requestedAt, null);
    }

    /**
     * Records a successful PG cancel of {@code cancelAmount}. Call it only after the PG confirmed the cancel.
     */
    public Payment cancel(Money cancelAmount, Instant now) {
        if (cancelAmount == null || cancelAmount.amount() == 0) {
            throw new InvalidValueException("INVALID_CANCEL_AMOUNT", "Cancel amount must be greater than zero");
        }
        if (status != PaymentStatus.APPROVED && status != PaymentStatus.PARTIALLY_CANCELLED) {
            throw new IllegalStateTransitionException("Payment", status, "cancel");
        }
        Money totalCancelled = cancelledAmount.plus(cancelAmount);
        if (totalCancelled.isGreaterThan(amount)) {
            throw new InvalidValueException("CANCEL_AMOUNT_EXCEEDED", "Cannot cancel more than the approved amount");
        }
        PaymentStatus next = totalCancelled.equals(amount) ? PaymentStatus.CANCELLED : PaymentStatus.PARTIALLY_CANCELLED;
        requireTransition(next);
        return new Payment(id, orderNo, idempotencyKey, amount, next, pgTid, totalCancelled, failureReason,
                requestedAt, approvedAt);
    }

    /** Amount that can still be cancelled; zero unless the payment is (partially) approved. */
    public Money cancelableAmount() {
        if (status != PaymentStatus.APPROVED && status != PaymentStatus.PARTIALLY_CANCELLED) {
            return Money.zero(amount.currency());
        }
        return amount.minus(cancelledAmount);
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    private void requireTransition(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Payment", status, target);
        }
    }

    public Long id() {
        return id;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public Money amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public PgTid pgTid() {
        return pgTid;
    }

    public Money cancelledAmount() {
        return cancelledAmount;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant approvedAt() {
        return approvedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Payment other && idempotencyKey.equals(other.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey);
    }
}
