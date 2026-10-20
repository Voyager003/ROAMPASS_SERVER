package com.roampass_server.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.application.PayResult;
import com.roampass_server.payment.application.PaymentService;
import com.roampass_server.payment.application.PaymentWebhookService;
import com.roampass_server.payment.application.WebhookResult;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.PaymentStatus;

@WebMvcTest(controllers = {PaymentController.class, PaymentWebhookController.class})
@DisplayName("결제 API")
class PaymentControllerTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentWebhookService webhookService;

    @Nested
    @DisplayName("POST /api/orders/{orderNo}/pay")
    class Pay {

        @Test
        @DisplayName("결제 결과(결제 상태·주문 상태·멱등 키)를 200으로 돌려준다")
        void pays() {
            // given
            given(paymentService.pay(UserId.of("user-1"), ORDER_NO)).willReturn(new PayResult(ORDER_NO,
                    IdempotencyKey.of(ORDER_NO, 1), PaymentStatus.APPROVED, OrderStatus.PAID));

            // when
            var result = mvc.post().uri("/api/orders/ORD-20261020-AAAA1111/pay").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                    {"orderNo":"ORD-20261020-AAAA1111","idempotencyKey":"ORD-20261020-AAAA1111-1",
                     "paymentStatus":"APPROVED","orderStatus":"PAID"}
                    """);
        }

        @Test
        @DisplayName("X-User-Id가 없으면 401")
        void requires_user() {
            // when
            var result = mvc.post().uri("/api/orders/ORD-20261020-AAAA1111/pay");

            // then
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(paymentService);
        }

        @Test
        @DisplayName("결제할 수 없는 주문이면 409와 코드")
        void not_payable() {
            // given
            given(paymentService.pay(any(), eq(ORDER_NO)))
                    .willThrow(new ConflictException("ORDER_NOT_PAYABLE", "Order is EXPIRED"));

            // when
            var result = mvc.post().uri("/api/orders/ORD-20261020-AAAA1111/pay").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_PAYABLE");
        }
    }

    @Nested
    @DisplayName("POST /api/payments/webhook")
    class Webhook {

        private static final String BODY = """
                {"eventId":"evt_1","eventType":"PAYMENT_APPROVED","idempotencyKey":"ORD-20261020-AAAA1111-1",
                 "pgTid":"pg_1","orderNo":"ORD-20261020-AAAA1111","amount":9900,"currency":"KRW",
                 "occurredAt":"2026-10-20T03:00:01Z"}
                """;

        @Test
        @DisplayName("본문 원문과 서명 헤더를 넘기고, 처리 결과를 200으로 돌려준다(인증 헤더 불필요)")
        void receives() {
            // given
            given(webhookService.receive(eq(BODY), eq("sig"), any())).willReturn(WebhookResult.PROCESSED);

            // when
            var result = mvc.post().uri("/api/payments/webhook").header("X-PG-Signature", "sig")
                    .contentType("application/json").content(BODY);

            // then
            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.result").isEqualTo("PROCESSED");
        }

        @Test
        @DisplayName("서명이 틀리면 401 INVALID_SIGNATURE")
        void invalid_signature() {
            // given
            given(webhookService.receive(any(), any(), any())).willThrow(new InvalidSignatureException());

            // when
            var result = mvc.post().uri("/api/payments/webhook").header("X-PG-Signature", "bad")
                    .contentType("application/json").content(BODY);

            // then
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SIGNATURE");
        }
    }
}
