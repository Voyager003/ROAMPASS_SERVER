package com.roampass_server.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.exception.InvalidValueException;

@DisplayName("RetryPolicy")
class RetryPolicyTest {

    @Test
    @DisplayName("기본값은 최대 3회, 1분부터 2배씩, 상한 30분이다")
    void defaults() {
        // when
        RetryPolicy policy = RetryPolicy.defaults();

        // then
        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.baseDelay()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.maxDelay()).isEqualTo(Duration.ofMinutes(30));
    }

    @Nested
    @DisplayName("다음 시도까지의 대기 시간은")
    class DelayAfter {

        private final RetryPolicy policy = RetryPolicy.of(10, Duration.ofMinutes(1), Duration.ofMinutes(30));

        @Test
        @DisplayName("실패할 때마다 두 배가 된다 (1분 → 2분 → 4분 → 8분)")
        void doubles_each_failure() {
            // when & then
            assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
            assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
            assertThat(policy.delayAfter(3)).isEqualTo(Duration.ofMinutes(4));
            assertThat(policy.delayAfter(4)).isEqualTo(Duration.ofMinutes(8));
        }

        @Test
        @DisplayName("상한을 넘지 않는다")
        void capped_at_max_delay() {
            // when & then
            assertThat(policy.delayAfter(6)).isEqualTo(Duration.ofMinutes(30)); // 32 → 30
            assertThat(policy.delayAfter(100)).isEqualTo(Duration.ofMinutes(30)); // 오버플로 없음
        }
    }

    @Test
    @DisplayName("실패 횟수가 최대 시도 횟수에 도달하면 소진된 것이다")
    void exhausted_at_max_attempts() {
        // given
        RetryPolicy policy = RetryPolicy.defaults();

        // when & then
        assertThat(policy.isExhausted(2)).isFalse();
        assertThat(policy.isExhausted(3)).isTrue();
    }

    @Test
    @DisplayName("잘못된 설정은 INVALID_RETRY_POLICY로 거부된다")
    void rejects_invalid_settings() {
        // when & then
        assertThatThrownBy(() -> RetryPolicy.of(0, Duration.ofMinutes(1), Duration.ofMinutes(30)))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_RETRY_POLICY");
        assertThatThrownBy(() -> RetryPolicy.of(3, Duration.ZERO, Duration.ofMinutes(30)))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> RetryPolicy.of(3, Duration.ofMinutes(10), Duration.ofMinutes(5)))
                .isInstanceOf(InvalidValueException.class);
    }
}
