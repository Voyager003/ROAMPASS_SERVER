package com.roampass_server.issuance.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.callback.application.FakeCallbackLogRepository;
import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.FakeStatusHistoryRepository;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.order.application.FakeOrderRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.model.PlanSnapshot;
import com.roampass_server.payment.application.FakePaymentRepository;
import com.roampass_server.payment.application.FakeRefundAttemptRepository;
import com.roampass_server.payment.application.RefundOpener;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.plan.application.FakePlanRepository;
import com.roampass_server.plan.application.PlanQueryService;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.model.PlanSpec;
import com.roampass_server.support.MutableClock;

/**
 * 발급 유스케이스 테스트 공용 준비물. 테스트마다 새로 생성
 */
class IssuanceFixture {

    static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");
    static final Money PRICE = Money.krw(9_900);
    static final Money USIM_PRICE = Money.krw(11_900);
    static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(2);
    static final String SECRET = "test-callback-secret";
    static final String ICCID = "8982300912345678902";
    static final String ACTIVATION_CODE = "LPA:1$smdp.example$X1Y2";

    final MutableClock clock = new MutableClock(NOW);
    final FakeOrderRepository orders = new FakeOrderRepository();
    final FakePaymentRepository payments = new FakePaymentRepository();
    final FakeRefundAttemptRepository refunds = new FakeRefundAttemptRepository();
    final FakeStatusHistoryRepository histories = new FakeStatusHistoryRepository();
    final StatusHistoryRecorder recorder = new StatusHistoryRecorder(histories);
    final FakeIssuanceRequestRepository requests = new FakeIssuanceRequestRepository();
    final FakeSimProfileRepository profiles = new FakeSimProfileRepository(
            orderNo -> orders.findByOrderNo(orderNo).orElseThrow().travelStartDate());
    final FakeCallbackLogRepository callbackLogs = new FakeCallbackLogRepository();
    final FakeSupplierClient supplier = new FakeSupplierClient();
    final HmacSignature signer = new HmacSignature(SECRET);
    final PlanQueryService planQuery = new PlanQueryService(new FakePlanRepository()
            .add(Plan.of(1L, PlanSpec.of("JP", 5, 3_072), SimType.ESIM, PRICE))
            .add(Plan.of(3L, PlanSpec.of("JP", 5, 3_072), SimType.USIM, USIM_PRICE)));

    final RefundOpener refundOpener = new RefundOpener(payments, refunds, recorder);
    final IssuanceFailureService failure = new IssuanceFailureService(requests, orders, refundOpener, recorder, clock);
    final IssuanceStartService start = new IssuanceStartService(orders, requests, recorder, clock);
    final IssuanceAcceptanceService acceptance = new IssuanceAcceptanceService(requests);
    final IssuanceDispatchService dispatch = new IssuanceDispatchService(orders, requests, start, acceptance, failure,
            supplier, 50);
    final IssuanceTimeoutService timeout = new IssuanceTimeoutService(requests, failure, clock, CALLBACK_TIMEOUT, 50);
    final IssuanceCallbackService callbacks = new IssuanceCallbackService(signer, callbackLogs,
            new IssuanceCallbackProcessor(callbackLogs, requests, profiles, orders, planQuery, failure, recorder,
                    clock), clock);

    final ProfileSignalApplier applier = new ProfileSignalApplier(profiles, recorder, clock);
    final ProfileTrackingService tracking = new ProfileTrackingService(profiles, supplier, applier, clock, 3, 100);
    final QrViewService qrView = new QrViewService(orders, profiles, recorder, clock);
    final UsimRegistrationService usim = new UsimRegistrationService(
            new UsimRegistrationStartService(orders, profiles, planQuery, recorder, clock), supplier, applier, profiles);

    /** 결제가 끝난 유심 주문(PICKUP_READY) */
    Order pickupReadyUsimOrder(OrderNo orderNo) {
        Order order = Order.create(orderNo, UserId.of("user-1"), PlanSnapshot.of(3L, SimType.USIM, USIM_PRICE),
                LocalDate.of(2026, 10, 25), NOW, Duration.ofMinutes(10)).confirmPayment(NOW);
        return orders.save(order);
    }

    /** 발급 완료(프로필 ISSUED)까지 진행, 여행 시작일 2026-10-25·사용 5일 */
    void issued() {
        String sup = dispatched();
        sendCallback(issued("sevt_issued", sup));
    }

    /** 결제가 끝난 이심 주문(PAID) + 승인된 결제 */
    Order paidEsimOrder() {
        Order order = Order.create(ORDER_NO, UserId.of("user-1"), PlanSnapshot.of(1L, SimType.ESIM, PRICE),
                LocalDate.of(2026, 10, 25), NOW, Duration.ofMinutes(10)).confirmPayment(NOW);
        payments.save(Payment.request(ORDER_NO, 1, PRICE, NOW).approve(PgTid.of("pg_1"), PRICE, NOW));
        return orders.save(order);
    }

    OrderStatus orderStatus() {
        return orders.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    CallbackResult sendCallback(IssuanceCallbackCommand command) {
        String body = "callback-" + command.eventId() + "-" + command.eventType(); // 서명 대상 원문(내용 무관)
        return callbacks.receive(body, signer.sign(body), raw -> command);
    }

    IssuanceCallbackCommand issued(String eventId, String supplierOrderNo) {
        return new IssuanceCallbackCommand(eventId, "ISSUED", supplierOrderNo, ORDER_NO.value(), ICCID,
                ACTIVATION_CODE, null);
    }

    IssuanceCallbackCommand issueFailed(String eventId, String supplierOrderNo) {
        return new IssuanceCallbackCommand(eventId, "ISSUE_FAILED", supplierOrderNo, ORDER_NO.value(), null, null,
                "OUT_OF_STOCK");
    }

    /** 발급 요청까지 진행(접수 번호 SUP-{멱등 키}) */
    String dispatched() {
        paidEsimOrder();
        dispatch.dispatch();
        return "SUP-" + ORDER_NO.value() + "-ISSUE-1";
    }
}
