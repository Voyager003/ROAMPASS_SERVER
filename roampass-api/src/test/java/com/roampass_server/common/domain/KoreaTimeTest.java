package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KoreaTime")
class KoreaTimeTest {

    @Test
    @DisplayName("기준 시간대는 Asia/Seoul이다")
    void zone_is_asia_seoul() {
        // when & then
        assertThat(KoreaTime.ZONE).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Nested
    @DisplayName("UTC 시각을 KST로 바꾸면")
    class WhenConvertingUtcToKst {

        @Test
        @DisplayName("UTC 15:00 이후는 KST로 다음 날이 된다 (부록 A 예시 시각)")
        void utc_after_15_is_next_day_in_kst() {
            // given — the activatedAt example from the assignment's Appendix A
            Instant utc = Instant.parse("2026-05-01T15:20:00Z");

            // when
            LocalDate kstDate = KoreaTime.toDate(utc);

            // then
            assertThat(kstDate).isEqualTo(LocalDate.of(2026, 5, 2));
        }

        @Test
        @DisplayName("UTC 14:59:59는 KST로 같은 날 23:59:59다 (자정 경계)")
        void utc_just_before_15_is_same_day_in_kst() {
            // given
            Instant utc = Instant.parse("2026-05-01T14:59:59Z");

            // when
            LocalDateTime kst = KoreaTime.toDateTime(utc);

            // then
            assertThat(kst).isEqualTo(LocalDateTime.of(2026, 5, 1, 23, 59, 59));
        }

        @Test
        @DisplayName("UTC 15:00:00 정각은 KST로 다음 날 00:00이다 (자정 경계)")
        void utc_exactly_15_is_next_day_midnight_in_kst() {
            // given
            Instant utc = Instant.parse("2026-05-01T15:00:00Z");

            // when
            LocalDateTime kst = KoreaTime.toDateTime(utc);

            // then
            assertThat(kst).isEqualTo(LocalDateTime.of(2026, 5, 2, 0, 0, 0));
        }
    }
}
