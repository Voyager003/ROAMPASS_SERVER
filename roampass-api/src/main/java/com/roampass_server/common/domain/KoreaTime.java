package com.roampass_server.common.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * UTC 시각을 운영 기준 시간대(KST)로 변환
 * 모든 시각은 UTC {@link Instant}로 저장, 한국 기준 날짜·시각이 실제로 필요할 때만
 * (만료일 계산, 화면 표시) 변환
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
