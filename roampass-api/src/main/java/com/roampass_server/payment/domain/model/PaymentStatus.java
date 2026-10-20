package com.roampass_server.payment.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of one payment attempt. A REQUESTED payment whose PG response was lost stays REQUESTED
 * until a status lookup or a webhook settles it. APPROVED and FAILED are never flipped into each other.
 */
public enum PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
    PARTIALLY_CANCELLED,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(REQUESTED, EnumSet.of(APPROVED, FAILED));
        ALLOWED.put(APPROVED, EnumSet.of(PARTIALLY_CANCELLED, CANCELLED));
        ALLOWED.put(PARTIALLY_CANCELLED, EnumSet.of(PARTIALLY_CANCELLED, CANCELLED)); // several partial cancels
        ALLOWED.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
