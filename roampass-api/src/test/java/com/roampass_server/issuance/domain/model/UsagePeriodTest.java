package com.roampass_server.issuance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.InvalidValueException;

@DisplayName("UsagePeriod (만료일: 개통 시각 + 24시간 × N)")
class UsagePeriodTest {

    // 부록 A 예시: activatedAt = 2026-05-01T15:20:00Z, 즉 KST 2026-05-02 00:20
    private static final Instant ACTIVATED_AT = Instant.parse("2026-05-01T15:20:00Z");

    @Nested
    @DisplayName("부록 A 예시(7일 요금제, 2026-05-01T15:20:00Z 개통)")
    class AppendixExample {

        private final UsagePeriod period = UsagePeriod.of(ACTIVATED_AT, 7);

        @Test
        @DisplayName("만료 시각은 정확히 168시간 뒤인 2026-05-08T15:20:00Z다")
        void expires_exactly_168_hours_later() {
            // when & then
            assertThat(period.expiresAt()).isEqualTo(Instant.parse("2026-05-08T15:20:00Z"));
            assertThat(Duration.between(ACTIVATED_AT, period.expiresAt())).isEqualTo(Duration.ofHours(168));
        }

        @Test
        @DisplayName("한국 시간으로는 2026-05-09 00:20에 만료된다 (UTC 날짜 5/8과 다름)")
        void expires_on_may_9th_in_kst() {
            // when & then
            assertThat(period.expiresAtKst()).isEqualTo(LocalDateTime.of(2026, 5, 9, 0, 20));
        }
    }

    @Nested
    @DisplayName("만료 여부와 남은 시간은")
    class ExpiryAndRemaining {

        private final UsagePeriod period = UsagePeriod.of(ACTIVATED_AT, 1);

        @Test
        @DisplayName("만료 1초 전에는 만료가 아니고 1초가 남는다")
        void one_second_before_expiry() {
            // given
            Instant now = period.expiresAt().minusSeconds(1);

            // when & then
            assertThat(period.isExpired(now)).isFalse();
            assertThat(period.remaining(now)).isEqualTo(Duration.ofSeconds(1));
        }

        @Test
        @DisplayName("만료 시각이 되면 만료이고 남은 시간은 0이다")
        void at_expiry() {
            // given
            Instant now = period.expiresAt();

            // when & then
            assertThat(period.isExpired(now)).isTrue();
            assertThat(period.remaining(now)).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("만료 이후에도 남은 시간은 음수가 아니라 0이다")
        void remaining_never_negative() {
            // when & then
            assertThat(period.remaining(period.expiresAt().plusSeconds(3600))).isEqualTo(Duration.ZERO);
        }
    }

    @Test
    @DisplayName("사용 일수가 1보다 작거나 개통 시각이 없으면 거부된다")
    void rejects_invalid_input() {
        // when & then
        assertThatThrownBy(() -> UsagePeriod.of(ACTIVATED_AT, 0))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_USAGE_PERIOD");
        assertThatThrownBy(() -> UsagePeriod.of(null, 7))
                .isInstanceOf(InvalidValueException.class);
    }
}
