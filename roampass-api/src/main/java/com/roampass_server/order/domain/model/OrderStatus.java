package com.roampass_server.order.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle: from payment to handing out the SIM (or refunding).
 * What happens to the SIM on the device afterwards (installed, activated, expired) is tracked by
 * SimProfile, not here. The allowed transitions are defined in one place below.
 */
public enum OrderStatus {
    /** Created, waiting for payment until expiresAt. */
    PENDING,
    /** Payment window passed without payment. */
    EXPIRED,
    /** Paid eSIM order waiting for the issuance request. */
    PAID,
    /** Paid USIM order waiting for airport pickup and ICCID registration. */
    PICKUP_READY,
    /** Issuance requested from the supplier, waiting for the callback. */
    ISSUE_REQUESTED,
    /** SIM handed out (eSIM profile received / USIM ICCID registered). */
    ISSUED,
    /** Issuance finally failed while the money is still taken. */
    ISSUE_FAILED,
    /** Refund in progress (PG cancel may be retried). */
    REFUNDING,
    /** Refund completed. Terminal. */
    REFUNDED,
    /** Automatic handling gave up; an operator must act. */
    NEEDS_ATTENTION;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(PAID, PICKUP_READY, EXPIRED));
        ALLOWED.put(EXPIRED, EnumSet.of(REFUNDING));               // approval arrived after expiry
        ALLOWED.put(PAID, EnumSet.of(ISSUE_REQUESTED, REFUNDING));
        ALLOWED.put(PICKUP_READY, EnumSet.of(ISSUED, REFUNDING));
        ALLOWED.put(ISSUE_REQUESTED, EnumSet.of(ISSUED, ISSUE_FAILED));
        ALLOWED.put(ISSUED, EnumSet.of(REFUNDING));                // only while the SIM is refundable
        ALLOWED.put(ISSUE_FAILED, EnumSet.of(REFUNDING));
        ALLOWED.put(REFUNDING, EnumSet.of(REFUNDED, NEEDS_ATTENTION));
        ALLOWED.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(NEEDS_ATTENTION, EnumSet.of(REFUNDING, ISSUE_REQUESTED));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
