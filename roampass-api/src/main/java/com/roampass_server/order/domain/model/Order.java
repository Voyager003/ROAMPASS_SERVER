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
 * 주문(집합 루트): 사용자 한 명이 산 SIM 하나. 결제부터 SIM을 내주거나 환불할 때까지 담당
 * 불변 — 모든 상태 변경은 새 인스턴스를 반환하고 반드시 {@link OrderStatus}의 전이표를 거침
 * 식별은 주문번호, 주문번호는 저장 전부터 존재
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
     * 새 PENDING 주문 생성
     *
     * @param now           현재 시각(애플리케이션의 Clock에서 획득)
     * @param paymentWindow 주문 만료 전까지 결제를 기다리는 시간
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

    // "오늘"은 UTC 날짜가 아닌 한국의 운영일
    private static void validateTravelStartDate(LocalDate travelStartDate, Instant now) {
        LocalDate todayKst = KoreaTime.toDate(now);
        if (travelStartDate.isBefore(todayKst) || travelStartDate.isAfter(todayKst.plusYears(MAX_DAYS_AHEAD_YEARS))) {
            throw new InvalidValueException("INVALID_TRAVEL_START_DATE",
                    "Travel start date must be between today (KST) and one year from today");
        }
    }

    /**
     * {@code now} 시점에 보이는 상태: 만료 시각이 지난 PENDING 주문은
     * 만료 스케줄러가 아직 처리하지 않았더라도 EXPIRED로 판정
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
     * 승인된 결제 반영. 결제 대기 시간 안이면 다음 단계로 진행(이심 → PAID, 유심 → PICKUP_READY)
     * 만료 시각 이후에 도착한 승인은 주문을 되살리지 않고 돈을 돌려주도록 REFUNDING으로 전이
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
     * 환불 시작
     *
     * @param simRefundable 발급된 SIM의 환불 가능 여부(설치·개통 전인지)
     *                      주문이 ISSUED일 때만 참고, 판정은 SIM 프로필 책임
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

    /** 자동 처리 포기(예: 환불 재시도 소진), 운영자 조치 필요 */
    public Order escalate() {
        return transitionTo(OrderStatus.NEEDS_ATTENTION);
    }

    /**
     * 운영자 재발급. 한 번도 발급된 적 없는 이심 주문만 허용해서
     * 한 주문에 프로필이 두 개 붙는 상황 방지
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
