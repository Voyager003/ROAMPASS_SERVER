package com.roampass_server.payment.application;

import static com.roampass_server.payment.application.PaymentFixture.ORDER_NO;
import static com.roampass_server.payment.application.PaymentFixture.PRICE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.service.PgInquiryResult;

@DisplayName("응답 유실 결제 정리")
class PaymentReconciliationServiceTest {

    private static final Duration STALE_AFTER = Duration.ofMinutes(1);

    private final PaymentFixture f = new PaymentFixture();
    private final FakePgClient pg = new FakePgClient();
    private final PaymentReconciliationService service = new PaymentReconciliationService(f.payments, pg,
            f.confirmation, f.clock, STALE_AFTER, 50);

    private Payment payment;

    private void givenStaleRequestedPayment() {
        f.pendingOrder(SimType.ESIM);
        payment = f.requestedPayment(1);
        f.clock.advance(STALE_AFTER);
    }

    private PaymentStatus paymentStatus() {
        return f.payments.findByIdempotencyKey(payment.idempotencyKey()).orElseThrow().status();
    }

    private OrderStatus orderStatus() {
        return f.orders.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    @Test
    @DisplayName("PG에 승인으로 남아 있으면 결제 APPROVED·주문 PAID, 이력 주체는 SYSTEM")
    void settles_approved() {
        // given
        givenStaleRequestedPayment();
        pg.inquiryReturns(payment.idempotencyKey(), PgInquiryResult.approved(PgTid.of("pg_1"), PRICE));

        // when
        int settled = service.reconcile();

        // then
        assertThat(settled).isEqualTo(1);
        assertThat(paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(f.histories.findByOrderNo(ORDER_NO))
                .filteredOn(h -> "APPROVED".equals(h.toStatus()))
                .extracting(StatusHistory::actor).containsExactly(Actor.system());
    }

    @Test
    @DisplayName("PG에 실패로 남아 있으면 결제 FAILED, 주문 PENDING(재결제 가능)")
    void settles_failed() {
        // given
        givenStaleRequestedPayment();
        pg.inquiryReturns(payment.idempotencyKey(), PgInquiryResult.failed());

        // when
        service.reconcile();

        // then
        assertThat(paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(orderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("PG가 요청을 받은 적 없으면(404) 결제 FAILED")
    void settles_not_received_as_failed() {
        // given
        givenStaleRequestedPayment();
        pg.inquiryReturns(payment.idempotencyKey(), PgInquiryResult.notFound());

        // when
        service.reconcile();

        // then
        assertThat(paymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("조회도 실패하면(UNKNOWN) REQUESTED로 두고 다음 주기에 다시 조회")
    void keeps_unknown() {
        // given
        givenStaleRequestedPayment();

        // when
        int settled = service.reconcile();

        // then
        assertThat(settled).isZero();
        assertThat(paymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(pg.inquiries()).containsExactly(payment.idempotencyKey());
    }

    @Test
    @DisplayName("요청한 지 기준 시간이 안 된 결제는 조회하지 않는다 (응답을 기다리는 중일 수 있음)")
    void skips_recent() {
        // given
        f.pendingOrder(SimType.ESIM);
        payment = f.requestedPayment(1);
        f.clock.advance(STALE_AFTER.minusSeconds(1));

        // when
        service.reconcile();

        // then
        assertThat(pg.inquiries()).isEmpty();
    }

    @Test
    @DisplayName("주문 만료 뒤 승인이 확인되면 주문 REFUNDING")
    void late_approval() {
        // given
        givenStaleRequestedPayment();
        f.clock.advance(Duration.ofMinutes(10));
        pg.inquiryReturns(payment.idempotencyKey(), PgInquiryResult.approved(PgTid.of("pg_1"), PRICE));

        // when
        service.reconcile();

        // then
        assertThat(orderStatus()).isEqualTo(OrderStatus.REFUNDING);
    }

    @Test
    @DisplayName("PG 승인 금액이 다르면 확정하지 않고 REQUESTED로 남긴다 (이상 건)")
    void amount_mismatch_is_not_settled() {
        // given
        givenStaleRequestedPayment();
        pg.inquiryReturns(payment.idempotencyKey(), PgInquiryResult.approved(PgTid.of("pg_1"), Money.krw(1)));

        // when
        int settled = service.reconcile();

        // then
        assertThat(settled).isZero();
        assertThat(paymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
    }
}
