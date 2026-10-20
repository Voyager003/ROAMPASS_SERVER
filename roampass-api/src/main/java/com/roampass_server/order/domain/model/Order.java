package com.roampass_server.order.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.KoreaTime;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;

/**
 * An order (aggregate root): one SIM bought by one user, from payment until the SIM is handed out or refunded.
 * Immutable — every state change returns a new instance, and every change goes through {@link OrderStatus}'s
 * transition table. Identity is the order number, which exists before the order is ever stored.
 */
public final class Order {

    private static final int MAX_DAYS_AHEAD_YEARS = 1;

    private final Long id;
    private final OrderNo orderNo;
    private final UserId userId;
    private final Long planId;
    private final SimType simType;
    private final Money amount;
    private final LocalDate travelStartDate;
    private final OrderStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant issuedAt;

    private Order(Long id, OrderNo orderNo, UserId userId, Long planId, SimType simType, Money amount,
                  LocalDate travelStartDate, OrderStatus status, Instant createdAt, Instant expiresAt,
                  Instant issuedAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.userId = userId;
        this.planId = planId;
        this.simType = simType;
        this.amount = amount;
        this.travelStartDate = travelStartDate;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.issuedAt = issuedAt;
    }

    /**
     * Creates a new PENDING order.
     *
     * @param now           current instant (from the application's Clock)
     * @param paymentWindow how long the order waits for payment before it expires
     */
    public static Order create(OrderNo orderNo, UserId userId, PlanSnapshot plan, LocalDate travelStartDate,
                               Instant now, Duration paymentWindow) {
        if (orderNo == null || userId == null || plan == null || travelStartDate == null || now == null) {
            throw new InvalidValueException("INVALID_ORDER", "Order number, user, plan, travel start date and time are required");
        }
        if (paymentWindow == null || paymentWindow.isZero() || paymentWindow.isNegative()) {
            throw new InvalidValueException("INVALID_ORDER", "Payment window must be positive");
        }
        validateTravelStartDate(travelStartDate, now);
        return new Order(null, orderNo, userId, plan.planId(), plan.simType(), plan.price(), travelStartDate,
                OrderStatus.PENDING, now, now.plus(paymentWindow), null);
    }

    // "Today" is the operating day in Korea, not the UTC date.
    private static void validateTravelStartDate(LocalDate travelStartDate, Instant now) {
        LocalDate todayKst = KoreaTime.toDate(now);
        if (travelStartDate.isBefore(todayKst) || travelStartDate.isAfter(todayKst.plusYears(MAX_DAYS_AHEAD_YEARS))) {
            throw new InvalidValueException("INVALID_TRAVEL_START_DATE",
                    "Travel start date must be between today (KST) and one year from today");
        }
    }

    /**
     * Status as seen at {@code now}: a PENDING order past its deadline is reported as EXPIRED
     * even if the expiration scheduler has not processed it yet.
     */
    public OrderStatus effectiveStatus(Instant now) {
        if (status == OrderStatus.PENDING && !now.isBefore(expiresAt)) {
            return OrderStatus.EXPIRED;
        }
        return status;
    }

    public boolean isPayable(Instant now) {
        return effectiveStatus(now) == OrderStatus.PENDING;
    }

    public Order expire(Instant now) {
        if (status == OrderStatus.PENDING && now.isBefore(expiresAt)) {
            throw new IllegalStateTransitionException("Order", status, OrderStatus.EXPIRED + " (payment window still open)");
        }
        return transitionTo(OrderStatus.EXPIRED);
    }

    /**
     * Applies an approved payment. Within the payment window the order moves on (eSIM → PAID, USIM → PICKUP_READY).
     * An approval that arrives after the deadline never revives the order: it goes to REFUNDING so the money is returned.
     */
    public Order confirmPayment(Instant now) {
        if (effectiveStatus(now) == OrderStatus.EXPIRED) {
            Order expired = status == OrderStatus.PENDING ? transitionTo(OrderStatus.EXPIRED) : this;
            return expired.transitionTo(OrderStatus.REFUNDING);
        }
        return transitionTo(simType == SimType.ESIM ? OrderStatus.PAID : OrderStatus.PICKUP_READY);
    }

    public Order requestIssuance() {
        return transitionTo(OrderStatus.ISSUE_REQUESTED);
    }

    public Order markIssued(Instant now) {
        Order issued = transitionTo(OrderStatus.ISSUED);
        return issued.withIssuedAt(now);
    }

    public Order markIssueFailed() {
        return transitionTo(OrderStatus.ISSUE_FAILED);
    }

    /**
     * Starts a refund.
     *
     * @param simRefundable whether the issued SIM is still refundable (not installed/activated).
     *                      Only consulted when the order is ISSUED; the SIM profile owns that judgement.
     */
    public Order startRefund(boolean simRefundable) {
        if (status == OrderStatus.ISSUED && !simRefundable) {
            throw new OrderNotRefundableException();
        }
        return transitionTo(OrderStatus.REFUNDING);
    }

    public Order completeRefund() {
        return transitionTo(OrderStatus.REFUNDED);
    }

    /** Automatic handling gave up (e.g. refund retries exhausted); an operator must act. */
    public Order escalate() {
        return transitionTo(OrderStatus.NEEDS_ATTENTION);
    }

    /**
     * Operator re-issuance. Allowed only for an eSIM order that has never been issued,
     * so a single order can never end up with two profiles.
     */
    public Order reissue() {
        if (simType != SimType.ESIM || issuedAt != null) {
            throw new IllegalStateTransitionException("Order", status, OrderStatus.ISSUE_REQUESTED + " (only never-issued eSIM orders)");
        }
        return transitionTo(OrderStatus.ISSUE_REQUESTED);
    }

    public boolean isOwnedBy(UserId user) {
        return userId.equals(user);
    }

    private Order transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException("Order", status, target);
        }
        return new Order(id, orderNo, userId, planId, simType, amount, travelStartDate, target, createdAt, expiresAt, issuedAt);
    }

    private Order withIssuedAt(Instant at) {
        return new Order(id, orderNo, userId, planId, simType, amount, travelStartDate, status, createdAt, expiresAt, at);
    }

    public Long id() {
        return id;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public UserId userId() {
        return userId;
    }

    public Long planId() {
        return planId;
    }

    public SimType simType() {
        return simType;
    }

    public Money amount() {
        return amount;
    }

    public LocalDate travelStartDate() {
        return travelStartDate;
    }

    public OrderStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Order other && orderNo.equals(other.orderNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNo);
    }
}
