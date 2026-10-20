package com.roampass_server.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;

@DisplayName("Payment")
class PaymentTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-7K3Q9XPA");
    private static final Money AMOUNT = Money.krw(12_000);
    private static final PgTid TID = PgTid.of("pg-tid-1");

    private static Payment requested() {
        return Payment.request(ORDER_NO, 1, AMOUNT, NOW);
    }

    private static Payment approved() {
        return requested().approve(TID, AMOUNT, NOW.plusSeconds(3));
    }

    @Nested
    @DisplayName("결제를 요청하면")
    class WhenRequested {

        @Test
        @DisplayName("REQUESTED이고 주문번호·시도 번호로 만든 멱등 키를 가진다")
        void starts_requested() {
            // when
            Payment payment = requested();

            // then
            assertThat(payment.status()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(payment.orderNo()).isEqualTo(ORDER_NO);
            assertThat(payment.idempotencyKey()).isEqualTo(IdempotencyKey.of(ORDER_NO, 1));
            assertThat(payment.amount()).isEqualTo(AMOUNT);
            assertThat(payment.cancelledAmount()).isEqualTo(Money.krw(0));
            assertThat(payment.pgTid()).isNull();
            assertThat(payment.requestedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("0원 결제는 거부된다")
        void rejects_zero_amount() {
            // when & then
            assertThatThrownBy(() -> Payment.request(ORDER_NO, 1, Money.krw(0), NOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PAYMENT");
        }
    }

    @Nested
    @DisplayName("승인 통지가 오면")
    class WhenApproved {

        @Test
        @DisplayName("금액이 같으면 APPROVED가 되고 PG 거래 ID와 승인 시각이 기록된다")
        void approves_when_amount_matches() {
            // given
            Instant approvedAt = NOW.plusSeconds(3);

            // when
            Payment payment = requested().approve(TID, AMOUNT, approvedAt);

            // then
            assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(payment.pgTid()).isEqualTo(TID);
            assertThat(payment.approvedAt()).isEqualTo(approvedAt);
            assertThat(payment.isApproved()).isTrue();
        }

        @Test
        @DisplayName("금액이 다르면 PAYMENT_AMOUNT_MISMATCH로 거부되고 결제는 그대로 REQUESTED다")
        void rejects_amount_mismatch() {
            // given
            Payment payment = requested();

            // when & then
            assertThatThrownBy(() -> payment.approve(TID, Money.krw(1_000), NOW))
                    .isInstanceOf(PaymentAmountMismatchException.class)
                    .extracting("code").isEqualTo("PAYMENT_AMOUNT_MISMATCH");
            assertThat(payment.status()).isEqualTo(PaymentStatus.REQUESTED);
        }

        @Test
        @DisplayName("같은 승인(같은 PG 거래 ID)이 다시 오면 아무것도 바뀌지 않는다 (응답 + 웹훅, 중복 웹훅)")
        void same_approval_again_is_noop() {
            // given
            Payment payment = approved();

            // when
            Payment again = payment.approve(TID, AMOUNT, NOW.plusSeconds(10));

            // then
            assertThat(again).isSameAs(payment);
            assertThat(again.approvedAt()).isEqualTo(NOW.plusSeconds(3));
        }

        @Test
        @DisplayName("중복 통지라도 금액이 다르면 거부된다")
        void duplicate_with_different_amount_is_rejected() {
            // when & then
            assertThatThrownBy(() -> approved().approve(TID, Money.krw(99_000), NOW))
                    .isInstanceOf(PaymentAmountMismatchException.class);
        }

        @Test
        @DisplayName("이미 승인된 결제에 다른 PG 거래 ID로 승인이 오면 거부된다")
        void different_approval_for_approved_payment_is_rejected() {
            // when & then
            assertThatThrownBy(() -> approved().approve(PgTid.of("pg-tid-OTHER"), AMOUNT, NOW))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("이미 실패로 확정된 결제는 승인으로 뒤집히지 않는다")
        void failed_payment_is_not_flipped_to_approved() {
            // given
            Payment failed = requested().fail("CARD_DECLINED", NOW);

            // when & then
            assertThatThrownBy(() -> failed.approve(TID, AMOUNT, NOW.plusSeconds(5)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("승인이 실패하면")
    class WhenFailed {

        @Test
        @DisplayName("FAILED가 되고 사유가 기록된다")
        void fails_with_reason() {
            // when
            Payment failed = requested().fail("CARD_DECLINED", NOW);

            // then
            assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(failed.failureReason()).isEqualTo("CARD_DECLINED");
            assertThat(failed.isApproved()).isFalse();
        }

        @Test
        @DisplayName("실패 통지가 다시 오면 아무것도 바뀌지 않는다")
        void repeated_failure_is_noop() {
            // given
            Payment failed = requested().fail("CARD_DECLINED", NOW);

            // when
            Payment again = failed.fail("CARD_DECLINED", NOW.plusSeconds(5));

            // then
            assertThat(again).isSameAs(failed);
        }

        @Test
        @DisplayName("이미 승인된 결제는 실패로 뒤집히지 않는다")
        void approved_payment_is_not_flipped_to_failed() {
            // when & then
            assertThatThrownBy(() -> approved().fail("LATE_FAILURE", NOW))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("취소하면")
    class WhenCancelled {

        @Test
        @DisplayName("전액 취소하면 CANCELLED가 되고 취소 가능 금액은 0이다")
        void full_cancel() {
            // when
            Payment cancelled = approved().cancel(AMOUNT, NOW.plusSeconds(60));

            // then
            assertThat(cancelled.status()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(cancelled.cancelledAmount()).isEqualTo(AMOUNT);
            assertThat(cancelled.cancelableAmount()).isEqualTo(Money.krw(0));
        }

        @Test
        @DisplayName("일부만 취소하면 PARTIALLY_CANCELLED가 되고 남은 금액을 다시 취소할 수 있다")
        void partial_then_rest() {
            // when
            Payment partial = approved().cancel(Money.krw(5_000), NOW.plusSeconds(60));
            Payment rest = partial.cancel(Money.krw(7_000), NOW.plusSeconds(120));

            // then
            assertThat(partial.status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELLED);
            assertThat(partial.cancelableAmount()).isEqualTo(Money.krw(7_000));
            assertThat(rest.status()).isEqualTo(PaymentStatus.CANCELLED);
        }

        @Test
        @DisplayName("부분 취소를 여러 번 할 수 있다")
        void multiple_partial_cancels() {
            // when
            Payment payment = approved()
                    .cancel(Money.krw(2_000), NOW.plusSeconds(60))
                    .cancel(Money.krw(3_000), NOW.plusSeconds(120));

            // then
            assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_CANCELLED);
            assertThat(payment.cancelledAmount()).isEqualTo(Money.krw(5_000));
        }

        @Test
        @DisplayName("승인 금액보다 많이 취소하면 CANCEL_AMOUNT_EXCEEDED로 거부된다")
        void cannot_cancel_more_than_approved() {
            // given
            Payment partial = approved().cancel(Money.krw(10_000), NOW.plusSeconds(60));

            // when & then
            assertThatThrownBy(() -> partial.cancel(Money.krw(2_001), NOW.plusSeconds(120)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("CANCEL_AMOUNT_EXCEEDED");
        }

        @Test
        @DisplayName("0원 취소는 거부된다")
        void rejects_zero_cancel() {
            // when & then
            assertThatThrownBy(() -> approved().cancel(Money.krw(0), NOW))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("승인 전 결제는 취소할 수 없다")
        void cannot_cancel_before_approval() {
            // when & then
            assertThatThrownBy(() -> requested().cancel(AMOUNT, NOW))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("이미 전액 취소된 결제는 더 취소할 수 없다")
        void cannot_cancel_after_full_cancel() {
            // given
            Payment cancelled = approved().cancel(AMOUNT, NOW.plusSeconds(60));

            // when & then
            assertThatThrownBy(() -> cancelled.cancel(Money.krw(1), NOW.plusSeconds(120)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Test
    @DisplayName("멱등 키가 같으면 같은 결제다 (시도 1건 = 결제 1건)")
    void equality_is_by_idempotency_key() {
        // when & then
        assertThat(approved()).isEqualTo(requested());
        assertThat(Payment.request(ORDER_NO, 2, AMOUNT, NOW)).isNotEqualTo(requested());
    }
}
