package com.roampass_server.payment.domain.model;

import java.time.Instant;
import java.util.Objects;

import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;

/**
 * 주문에 대한 결제 시도 1건(집합 루트). 불변, 식별은 멱등 키
 * <ul>
 *   <li>승인 멱등: 같은 승인(같은 PG 거래 ID)이 다시 와도 변화 없음,
 *       승인 응답과 하나 이상의 웹훅이 와도 한 번만 반영</li>
 *   <li>결제 금액과 다른 금액의 승인은 거부</li>
 *   <li>취소는 누적, 승인 금액 초과 불가</li>
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
     * 새 결제 시도 시작
     *
     * @param attempt 주문의 첫 시도는 1, 첫 시도 실패 후에는 2, ...
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
     * 승인 통지(PG 응답 또는 웹훅) 반영
     * 같은 승인이 이미 반영되어 있으면 {@code this}를 그대로 반환
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
                return this; // 이미 반영한 승인의 중복 통지
            }
            throw new IllegalStateTransitionException("Payment", status, PaymentStatus.APPROVED + " (different PG transaction id)");
        }
        requireTransition(PaymentStatus.APPROVED);
        return new Payment(id, orderNo, idempotencyKey, amount, PaymentStatus.APPROVED, tid, cancelledAmount,
                null, requestedAt, now);
    }

    /**
     * 실패 통지 반영. 같은 실패 통지가 다시 오면 변화 없음, 승인된 결제는 뒤집지 않음
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
     * {@code cancelAmount}만큼의 PG 취소 성공 기록. PG가 취소를 확인한 뒤에만 호출
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

    /** 아직 취소할 수 있는 금액, (부분) 승인 상태가 아니면 0 */
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
