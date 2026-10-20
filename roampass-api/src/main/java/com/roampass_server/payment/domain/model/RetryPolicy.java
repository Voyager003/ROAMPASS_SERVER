package com.roampass_server.payment.domain.model;

import java.time.Duration;

import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 실패한 작업의 재시도 횟수와 대기 시간(지수 백오프)
 * 재시도는 항상 제한: {@link #maxAttempts()}번 실패하면 운영자에게 인계
 *
 * @param maxAttempts 첫 시도를 포함한 전체 허용 시도 횟수
 * @param baseDelay   첫 실패 후의 대기 시간, 이후 실패할 때마다 두 배
 * @param maxDelay    한 번 대기하는 시간의 상한
 */
public record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {

    private static final String CODE = "INVALID_RETRY_POLICY";
    // 기본 대기 시간이 얼마든 2^30배면 상한을 훨씬 넘으므로 지수에 상한을 두어 오버플로 방지
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

    /** 3회, 1분 대기 후 2분, 4분 ... 최대 30분 */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMinutes(1), Duration.ofMinutes(30));
    }

    /** {@code failedAttempts}번 실패한 뒤 다음 시도까지의 대기 시간(1 = 첫 실패) */
    public Duration delayAfter(int failedAttempts) {
        int shift = Math.min(Math.max(failedAttempts - 1, 0), MAX_SHIFT);
        Duration delay = baseDelay.multipliedBy(1L << shift);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    public boolean isExhausted(int failedAttempts) {
        return failedAttempts >= maxAttempts;
    }
}
