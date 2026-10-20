package com.roampass_server.common.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Conversions from UTC instants to the operating time zone (KST).
 * Everything is stored as UTC {@link Instant}; convert only when a calendar date or
 * wall-clock time in Korea is actually needed (expiry dates, display).
 */
public final class KoreaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    public static LocalDate toDate(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    public static LocalDateTime toDateTime(Instant instant) {
        return instant.atZone(ZONE).toLocalDateTime();
    }
}
