package com.roampass_server.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;

@DisplayName("RefundAttempt")
class RefundAttemptTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-7K3Q9XPA");
    private static final IdempotencyKey PAYMENT_KEY = IdempotencyKey.of(ORDER_NO, 1);
    private static final Money AMOUNT = Money.krw(12_000);
    private static final RetryPolicy POLICY = RetryPolicy.defaults(); // 3 attempts, 1m → 2m → ...

    private static RefundAttempt started() {
        return RefundAttempt.start(PAYMENT_KEY, ORDER_NO, AMOUNT, NOW);
    }

    @Nested
    @DisplayName("환불을 시작하면")
    class WhenStarted {

        @Test
        @DisplayName("PENDING이고 시도 0회이며 바로 처리 대상이다")
        void starts_pending_and_due_now() {
            // when
            RefundAttempt refund = started();

            // then
            assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
            assertThat(refund.attemptCount()).isZero();
            assertThat(refund.nextRetryAt()).isEqualTo(NOW);
            assertThat(refund.isDue(NOW)).isTrue();
            assertThat(refund.paymentKey()).isEqualTo(PAYMENT_KEY);
            assertThat(refund.orderNo()).isEqualTo(ORDER_NO);
            assertThat(refund.amount()).isEqualTo(AMOUNT);
        }

        @Test
        @DisplayName("0원 환불은 거부된다")
        void rejects_zero_amount() {
            // when & then
            assertThatThrownBy(() -> RefundAttempt.start(PAYMENT_KEY, ORDER_NO, Money.krw(0), NOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_REFUND");
        }
    }

    @Nested
    @DisplayName("PG 취소가 성공하면")
    class WhenSucceeded {

        @Test
        @DisplayName("SUCCEEDED가 되고 더 이상 처리 대상이 아니다")
        void succeeds() {
            // when
            RefundAttempt refund = started().recordSuccess(NOW.plusSeconds(2));

            // then
            assertThat(refund.status()).isEqualTo(RefundStatus.SUCCEEDED);
            assertThat(refund.completedAt()).isEqualTo(NOW.plusSeconds(2));
            assertThat(refund.isDue(NOW.plusSeconds(3600))).isFalse();
        }

        @Test
        @DisplayName("이미 성공한 환불에 다시 성공을 기록하면 거부된다")
        void cannot_succeed_twice() {
            // given
            RefundAttempt succeeded = started().recordSuccess(NOW);

            // when & then
            assertThatThrownBy(() -> succeeded.recordSuccess(NOW.plusSeconds(1)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("PG 취소가 실패하면")
    class WhenFailed {

        @Test
        @DisplayName("시도 횟수가 오르고, 지수 백오프로 정한 시각까지는 처리 대상이 아니다")
        void schedules_next_retry() {
            // when
            RefundAttempt refund = started().recordFailure("PG 503", NOW, POLICY);

            // then
            assertThat(refund.status()).isEqualTo(RefundStatus.PENDING);
            assertThat(refund.attemptCount()).isEqualTo(1);
            assertThat(refund.lastError()).isEqualTo("PG 503");
            assertThat(refund.nextRetryAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
            assertThat(refund.isDue(NOW.plusSeconds(59))).isFalse();
            assertThat(refund.isDue(NOW.plusSeconds(60))).isTrue();
        }

        @Test
        @DisplayName("두 번째 실패 후에는 2분을 기다린다")
        void second_failure_waits_longer() {
            // given
            Instant secondTry = NOW.plus(Duration.ofMinutes(1));

            // when
            RefundAttempt refund = started()
                    .recordFailure("PG timeout", NOW, POLICY)
                    .recordFailure("PG timeout", secondTry, POLICY);

            // then
            assertThat(refund.attemptCount()).isEqualTo(2);
            assertThat(refund.nextRetryAt()).isEqualTo(secondTry.plus(Duration.ofMinutes(2)));
        }

        @Test
        @DisplayName("최대 시도 횟수(3회)에 도달하면 GAVE_UP이 되고 더 이상 재시도하지 않는다")
        void gives_up_after_max_attempts() {
            // when
            RefundAttempt refund = started()
                    .recordFailure("PG 503", NOW, POLICY)
                    .recordFailure("PG 503", NOW.plusSeconds(60), POLICY)
                    .recordFailure("PG 503", NOW.plusSeconds(180), POLICY);

            // then
            assertThat(refund.status()).isEqualTo(RefundStatus.GAVE_UP);
            assertThat(refund.attemptCount()).isEqualTo(3);
            assertThat(refund.nextRetryAt()).isNull();
            assertThat(refund.isDue(NOW.plus(Duration.ofDays(1)))).isFalse();
            assertThat(refund.completedAt()).isEqualTo(NOW.plusSeconds(180));
        }

        @Test
        @DisplayName("포기한 환불에는 더 이상 실패를 기록할 수 없다 (무한 재시도 금지)")
        void cannot_retry_after_giving_up() {
            // given
            RefundAttempt gaveUp = started()
                    .recordFailure("e", NOW, POLICY)
                    .recordFailure("e", NOW, POLICY)
                    .recordFailure("e", NOW, POLICY);

            // when & then
            assertThatThrownBy(() -> gaveUp.recordFailure("e", NOW, POLICY))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("긴 에러 메시지는 500자로 잘린다")
        void truncates_long_error() {
            // when
            RefundAttempt refund = started().recordFailure("x".repeat(2_000), NOW, POLICY);

            // then
            assertThat(refund.lastError()).hasSize(500);
        }
    }
}
