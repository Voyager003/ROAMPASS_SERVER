package com.roampass_server.issuance.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.KoreaTime;

/**
 * 개통된 SIM의 사용 기간. N일 요금제는 공급사가 알려 준 개통 시각(UTC)부터 정확히 N × 24시간이라서
 * 하루 중 언제 개통하든 사용자는 항상 N일을 온전히 사용
 * 만료 시각은 KST로 표시, UTC와 달력 날짜가 다를 수 있음
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

    /** 만료까지 남은 시간, 음수 없음 */
    public Duration remaining(Instant now) {
        Duration left = Duration.between(now, expiresAt());
        return left.isNegative() ? Duration.ZERO : left;
    }
}
