package com.roampass_server.payment.application;

import static com.roampass_server.payment.application.PaymentFixture.ORDER_NO;
import static com.roampass_server.payment.application.PaymentFixture.PRICE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.model.RetryPolicy;
import com.roampass_server.payment.domain.service.PgCancelResult;

@DisplayName("환불 실행(PG 취소)")
class RefundExecutionServiceTest {

    private final PaymentFixture f = new PaymentFixture();
    private final FakePgClient pg = new FakePgClient();
    private final RefundExecutionService service = new RefundExecutionService(f.refunds, f.payments, pg,
            new RefundResultService(f.refunds, f.payments, f.orders, f.recorder, RetryPolicy.defaults(), f.clock),
            f.clock, 50);

    private RefundAttempt refund;

    /** 결제 승인 후 REFUNDING이 된 주문 + 환불 요청 */
    private void givenRefundingOrder() {
        Order order = f.pendingOrder(SimType.ESIM);
        f.orders.replace(order.confirmPayment(f.clock.instant()).startRefund(true));
        f.payments.save(Payment.request(ORDER_NO, 1, PRICE, f.clock.instant())
                .approve(PgTid.of("pg_1"), PRICE, f.clock.instant()));
        refund = f.refundOpener.open(ORDER_NO, Actor.system(), "test", f.clock.instant());
    }

    private RefundAttempt storedRefund() {
        return f.refunds.findByOrderNo(ORDER_NO).getFirst();
    }

    private OrderStatus orderStatus() {
        return f.orders.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    @Test
    @DisplayName("PG 취소가 성공하면 환불 SUCCEEDED, 결제 CANCELLED, 주문 REFUNDED (취소 키는 refund-{id}, 남은 금액 전부)")
    void refunds() {
        // given
        givenRefundingOrder();

        // when
        int refunded = service.processDue();

        // then
        assertThat(refunded).isEqualTo(1);
        assertThat(pg.cancels()).singleElement().satisfies(cancel -> {
            assertThat(cancel.cancelKey()).isEqualTo("refund-" + refund.id());
            assertThat(cancel.amount()).isEqualTo(PRICE);
            assertThat(cancel.pgTid()).isEqualTo(PgTid.of("pg_1"));
        });
        assertThat(storedRefund().status()).isEqualTo(RefundStatus.SUCCEEDED);
        assertThat(f.payments.findByOrderNo(ORDER_NO).getFirst().status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(orderStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("실패하면 1분 뒤로 재시도 예약, 3회 모두 실패하면 GAVE_UP·주문 NEEDS_ATTENTION·이력 흔적")
    void gives_up_after_three_failures() {
        // given
        givenRefundingOrder();
        pg.cancelReturns(PgCancelResult.unknown("read timeout"));

        // when: 1회 실패
        service.processDue();

        // then
        assertThat(storedRefund().attemptCount()).isEqualTo(1);
        assertThat(storedRefund().nextRetryAt()).isEqualTo(f.clock.instant().plus(Duration.ofMinutes(1)));
        assertThat(service.processDue()).isZero();       // 아직 시도 시각 전
        assertThat(pg.cancels()).hasSize(1);

        // when: 1분 뒤 2회, 2분 뒤 3회 실패
        f.clock.advance(Duration.ofMinutes(1));
        service.processDue();
        f.clock.advance(Duration.ofMinutes(2));
        service.processDue();

        // then
        assertThat(pg.cancels()).hasSize(3).extracting(c -> c.cancelKey()).containsOnly("refund-" + refund.id());
        assertThat(storedRefund().status()).isEqualTo(RefundStatus.GAVE_UP);
        assertThat(orderStatus()).isEqualTo(OrderStatus.NEEDS_ATTENTION);
        assertThat(f.histories.findByOrderNo(ORDER_NO))
                .filteredOn(h -> h.target().type() == TargetType.ORDER)
                .extracting(StatusHistory::toStatus).containsExactly("NEEDS_ATTENTION");
    }
}
