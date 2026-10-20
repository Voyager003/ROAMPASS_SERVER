package com.roampass_server.payment.application;

import static com.roampass_server.payment.application.PaymentFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.callback.application.FakeCallbackLogRepository;
import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;

@DisplayName("PG 웹훅 수신")
class PaymentWebhookServiceTest {

    private static final String SECRET = "test-secret";

    private final PaymentFixture f = new PaymentFixture();
    private final HmacSignature signer = new HmacSignature(SECRET);
    private final FakeCallbackLogRepository logs = new FakeCallbackLogRepository();
    private final PaymentWebhookService service = new PaymentWebhookService(signer, logs,
            new PaymentWebhookProcessor(logs, f.confirmation, f.clock), f.clock);

    private Payment payment;

    private PaymentWebhookCommand approved(String eventId, long amount) {
        return new PaymentWebhookCommand(eventId, "PAYMENT_APPROVED", payment.idempotencyKey().value(), "pg_1",
                amount, "KRW");
    }

    private WebhookResult send(PaymentWebhookCommand command) {
        String body = "body-of-" + command.eventId() + "-" + command.amount(); // 서명 대상 원문(내용은 무관)
        return service.receive(body, signer.sign(body), raw -> command);
    }

    private void givenPendingPayment() {
        f.pendingOrder(SimType.ESIM);
        payment = f.requestedPayment(1);
    }

    private PaymentStatus paymentStatus() {
        return f.payments.findByIdempotencyKey(payment.idempotencyKey()).orElseThrow().status();
    }

    @Nested
    @DisplayName("서명이 맞는 승인 웹훅이면")
    class ValidApproval {

        @Test
        @DisplayName("PROCESSED, 주문 PAID, 수신 기록(PG:{eventId}, 서명 OK, 결과 PROCESSED)")
        void processes() {
            // given
            givenPendingPayment();

            // when
            WebhookResult result = send(approved("evt_1", 9_900));

            // then
            assertThat(result).isEqualTo(WebhookResult.PROCESSED);
            assertThat(f.orders.findByOrderNo(ORDER_NO).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
            assertThat(logs.findByEventKey("PG:evt_1")).get().satisfies(log -> {
                assertThat(log.signatureOk()).isTrue();
                assertThat(log.outcome()).isEqualTo(CallbackOutcome.PROCESSED);
            });
        }

        @Test
        @DisplayName("같은 eventId가 다시 오면 DUPLICATE, 기록 1건·이력 변화 없음")
        void duplicate_event() {
            // given
            givenPendingPayment();
            send(approved("evt_1", 9_900));
            int histories = f.histories.all().size();

            // when
            WebhookResult result = send(approved("evt_1", 9_900));

            // then
            assertThat(result).isEqualTo(WebhookResult.DUPLICATE);
            assertThat(logs.all()).hasSize(1);
            assertThat(f.histories.all()).hasSize(histories);
        }

        @Test
        @DisplayName("승인 응답으로 이미 반영된 뒤 오면 ALREADY_APPLIED로 기록")
        void after_response_path() {
            // given
            givenPendingPayment();
            f.confirmation.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_1"), PaymentFixture.PRICE,
                    Actor.pg());

            // when
            WebhookResult result = send(approved("evt_1", 9_900));

            // then
            assertThat(result).isEqualTo(WebhookResult.ALREADY_APPLIED);
            assertThat(logs.findByEventKey("PG:evt_1")).get()
                    .extracting(CallbackLog::outcome).isEqualTo(CallbackOutcome.ALREADY_APPLIED);
        }
    }

    @Nested
    @DisplayName("금액이 주문 금액과 다르면")
    class AmountMismatch {

        @Test
        @DisplayName("AMOUNT_MISMATCH로 거절하고 이상 건으로 기록, 결제 REQUESTED 유지")
        void rejects_and_records() {
            // given
            givenPendingPayment();

            // when
            WebhookResult result = send(approved("evt_1", 10_000));

            // then
            assertThat(result).isEqualTo(WebhookResult.AMOUNT_MISMATCH);
            assertThat(paymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(logs.findByEventKey("PG:evt_1")).get()
                    .extracting(CallbackLog::outcome).isEqualTo(CallbackOutcome.AMOUNT_MISMATCH);
        }
    }

    @Nested
    @DisplayName("서명이 틀리면")
    class InvalidSignature {

        @Test
        @DisplayName("401 거절, 이벤트 키 없이 서명 실패로 기록, 결제 변화 없음")
        void rejects() {
            // given
            givenPendingPayment();

            // when // then
            assertThatThrownBy(() -> service.receive("body", "deadbeef", raw -> approved("evt_1", 9_900)))
                    .isInstanceOf(InvalidSignatureException.class);
            assertThat(logs.all()).singleElement().satisfies(log -> {
                assertThat(log.eventKey()).isNull();
                assertThat(log.signatureOk()).isFalse();
                assertThat(log.outcome()).isEqualTo(CallbackOutcome.SIGNATURE_INVALID);
            });
            assertThat(paymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
        }

        @Test
        @DisplayName("서명이 틀리면 본문을 해석하지 않는다")
        void does_not_parse_unverified_body() {
            // given
            givenPendingPayment();

            // when // then
            assertThatThrownBy(() -> service.receive("not-json", "deadbeef", raw -> {
                throw new AssertionError("parser must not be called");
            })).isInstanceOf(InvalidSignatureException.class);
        }

        @Test
        @DisplayName("위조 요청이 이벤트 키를 선점하지 못해, 같은 eventId의 진짜 웹훅은 정상 처리")
        void forged_request_cannot_claim_event_key() {
            // given
            givenPendingPayment();
            assertThatThrownBy(() -> service.receive("body", "deadbeef", raw -> approved("evt_1", 9_900)))
                    .isInstanceOf(InvalidSignatureException.class);

            // when
            WebhookResult result = send(approved("evt_1", 9_900));

            // then
            assertThat(result).isEqualTo(WebhookResult.PROCESSED);
        }
    }

    @Nested
    @DisplayName("기타 웹훅")
    class Others {

        @Test
        @DisplayName("실패 웹훅이면 결제 FAILED")
        void failure_webhook() {
            // given
            givenPendingPayment();

            // when
            WebhookResult result = send(new PaymentWebhookCommand("evt_1", "PAYMENT_FAILED",
                    payment.idempotencyKey().value(), null, 9_900, "KRW"));

            // then
            assertThat(result).isEqualTo(WebhookResult.PROCESSED);
            assertThat(paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("모르는 결제면 PAYMENT_NOT_FOUND로 기록")
        void unknown_payment() {
            // given
            givenPendingPayment();

            // when
            WebhookResult result = send(new PaymentWebhookCommand("evt_1", "PAYMENT_APPROVED",
                    "ORD-20261020-AAAA1111-9", "pg_1", 9_900, "KRW"));

            // then
            assertThat(result).isEqualTo(WebhookResult.PAYMENT_NOT_FOUND);
            assertThat(logs.findByEventKey("PG:evt_1")).get()
                    .extracting(CallbackLog::outcome).isEqualTo(CallbackOutcome.TARGET_NOT_FOUND);
        }
    }
}
