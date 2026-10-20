package com.roampass_server.payment.domain.model;

import java.time.Duration;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * How many times a failing operation is retried and how long to wait in between (exponential backoff).
 * Retries are always bounded: once {@link #maxAttempts()} attempts have failed, the work is handed to an operator.
 *
 * @param maxAttempts total attempts allowed, including the first one
 * @param baseDelay   wait after the first failure; doubled after each further failure
 * @param maxDelay    upper bound for a single wait
 */
public record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {

    private static final String CODE = "INVALID_RETRY_POLICY";
    // 2^30 times any sensible base delay is far above any max delay, so capping the exponent avoids overflow.
    private static final int MAX_SHIFT = 30;

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new InvalidValueException(CODE, "Max attempts must be at least 1");
        }
        if (baseDelay == null || baseDelay.isZero() || baseDelay.isNegative()) {
            throw new InvalidValueException(CODE, "Base delay must be positive");
        }
        if (maxDelay == null || maxDelay.compareTo(baseDelay) < 0) {
            throw new InvalidValueException(CODE, "Max delay must not be shorter than the base delay");
        }
    }

    public static RetryPolicy of(int maxAttempts, Duration baseDelay, Duration maxDelay) {
        return new RetryPolicy(maxAttempts, baseDelay, maxDelay);
    }

    /** 3 attempts, waiting 1 minute, then 2, then 4 ... at most 30 minutes. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMinutes(1), Duration.ofMinutes(30));
    }

    /** Wait before the next attempt after {@code failedAttempts} failures (1 = first failure). */
    public Duration delayAfter(int failedAttempts) {
        int shift = Math.min(Math.max(failedAttempts - 1, 0), MAX_SHIFT);
        Duration delay = baseDelay.multipliedBy(1L << shift);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    public boolean isExhausted(int failedAttempts) {
        return failedAttempts >= maxAttempts;
    }
}
