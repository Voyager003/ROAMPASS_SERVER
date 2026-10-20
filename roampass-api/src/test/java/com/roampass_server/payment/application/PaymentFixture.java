package com.roampass_server.payment.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.FakeStatusHistoryRepository;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.order.application.FakeOrderRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.PlanSnapshot;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.support.MutableClock;

/**
 * 결제 유스케이스 테스트 공용 준비물. 테스트마다 새로 생성
 */
class PaymentFixture {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    static final Duration WINDOW = Duration.ofMinutes(10);
    static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");
    static final UserId USER = UserId.of("user-1");
    static final Money PRICE = Money.krw(9_900);

    final MutableClock clock = new MutableClock(NOW);
    final FakeOrderRepository orders = new FakeOrderRepository();
    final FakePaymentRepository payments = new FakePaymentRepository();
    final FakeStatusHistoryRepository histories = new FakeStatusHistoryRepository();
    final StatusHistoryRecorder recorder = new StatusHistoryRecorder(histories);
    final PaymentConfirmationService confirmation = new PaymentConfirmationService(payments, orders, recorder, clock);

    Order pendingOrder(SimType simType) {
        return orders.save(Order.create(ORDER_NO, USER, PlanSnapshot.of(1L, simType, PRICE),
                LocalDate.of(2026, 10, 25), NOW, WINDOW));
    }

    Payment requestedPayment(int attempt) {
        return payments.save(Payment.request(ORDER_NO, attempt, PRICE, NOW));
    }
}
