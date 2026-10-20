package com.roampass_server.history.domain.model;

import java.time.Instant;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

/**
 * One status change: when, what (target, from → to), who or which signal (actor), and why (reason).
 * Append-only — there are no mutators. Every entry carries the order number so the admin timeline can show
 * order, payment, refund and SIM changes of one order in a single list.
 * Statuses are stored by enum name because different lifecycles (order, payment, install) share this record.
 * Operator actions must state a reason (assignment 2.3-⑤).
 */
public final class StatusHistory {

    private static final String CODE = "INVALID_HISTORY";
    private static final int MAX_REASON_LENGTH = 500;

    private final Long id;
    private final OrderNo orderNo;
    private final HistoryTarget target;
    private final String fromStatus;
    private final String toStatus;
    private final Actor actor;
    private final String reason;
    private final Instant occurredAt;

    private StatusHistory(Long id, OrderNo orderNo, HistoryTarget target, String fromStatus, String toStatus,
                          Actor actor, String reason, Instant occurredAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.target = target;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    /**
     * @param from previous status, or {@code null} when the target was just created
     */
    public static StatusHistory record(OrderNo orderNo, HistoryTarget target, Enum<?> from, Enum<?> to,
                                       Actor actor, String reason, Instant occurredAt) {
        if (orderNo == null || target == null || to == null || actor == null || occurredAt == null) {
            throw new InvalidValueException(CODE, "Order number, target, new status, actor and time are required");
        }
        if (from != null && from.name().equals(to.name())) {
            throw new InvalidValueException(CODE, "A history entry must record an actual status change");
        }
        if (actor.isAdmin() && (reason == null || reason.isBlank())) {
            throw new InvalidValueException("ADMIN_REASON_REQUIRED", "Operator actions must state a reason");
        }
        return new StatusHistory(null, orderNo, target, from == null ? null : from.name(), to.name(), actor,
                truncate(reason), occurredAt);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    public Long id() {
        return id;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public HistoryTarget target() {
        return target;
    }

    public String fromStatus() {
        return fromStatus;
    }

    public String toStatus() {
        return toStatus;
    }

    public Actor actor() {
        return actor;
    }

    public String reason() {
        return reason;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
