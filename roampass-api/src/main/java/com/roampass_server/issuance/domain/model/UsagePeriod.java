package com.roampass_server.issuance.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.KoreaTime;

/**
 * Usage window of an activated SIM. An N-day plan lasts exactly N × 24 hours from the supplier's activation time
 * (UTC), so the user always gets the full N days regardless of what time of day they activated.
 * The expiry is shown in KST, which can fall on a different calendar date than in UTC.
 */
public record UsagePeriod(Instant activatedAt, int days) {

    private static final long HOURS_PER_DAY = 24;

    public UsagePeriod {
        if (activatedAt == null || days < 1) {
            throw new InvalidValueException("INVALID_USAGE_PERIOD", "Activation time and at least one usage day are required");
        }
    }

    public static UsagePeriod of(Instant activatedAt, int days) {
        return new UsagePeriod(activatedAt, days);
    }

    public Instant expiresAt() {
        return activatedAt.plus(Duration.ofHours(HOURS_PER_DAY * days));
    }

    public LocalDateTime expiresAtKst() {
        return KoreaTime.toDateTime(expiresAt());
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt());
    }

    /** Time left until expiry; never negative. */
    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, expiresAt());
        return left.isNegative() ? Duration.ZERO : left;
    }
}
