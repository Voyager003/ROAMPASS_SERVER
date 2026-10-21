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
import com.roampass_server.payment.application.RefundResultService;
import com.roampass_server.payment.domain.model.RetryPolicy;
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
public class IssuanceFixture {

    public static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    public static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");
    public static final Money PRICE = Money.krw(9_900);
    public static final Money USIM_PRICE = Money.krw(11_900);
    public static final Duration CALLBACK_TIMEOUT = Duration.ofMinutes(2);
    public static final String SECRET = "test-callback-secret";
    public static final String ICCID = "8982300912345678902";
    public static final String ACTIVATION_CODE = "LPA:1$smdp.example$X1Y2";

    public final MutableClock clock = new MutableClock(NOW);
    public final FakeOrderRepository orders = new FakeOrderRepository();
    public final FakePaymentRepository payments = new FakePaymentRepository();
    public final FakeRefundAttemptRepository refunds = new FakeRefundAttemptRepository();
    public final FakeStatusHistoryRepository histories = new FakeStatusHistoryRepository();
    public final StatusHistoryRecorder recorder = new StatusHistoryRecorder(histories);
    public final FakeIssuanceRequestRepository requests = new FakeIssuanceRequestRepository();
    public final FakeSimProfileRepository profiles = new FakeSimProfileRepository(
            orderNo -> orders.findByOrderNo(orderNo).orElseThrow().travelStartDate());
    public final FakeCallbackLogRepository callbackLogs = new FakeCallbackLogRepository();
    public final FakeSupplierClient supplier = new FakeSupplierClient();
    public final HmacSignature signer = new HmacSignature(SECRET);
    public final PlanQueryService planQuery = new PlanQueryService(new FakePlanRepository()
            .add(Plan.of(1L, PlanSpec.of("JP", 5, 3_072), SimType.ESIM, PRICE))
            .add(Plan.of(3L, PlanSpec.of("JP", 5, 3_072), SimType.USIM, USIM_PRICE)));

    public final RefundOpener refundOpener = new RefundOpener(payments, refunds, recorder);
    public final IssuanceFailureService failure = new IssuanceFailureService(requests, orders, refundOpener, recorder, clock);
    public final IssuanceStartService start = new IssuanceStartService(orders, requests, recorder, clock);
    public final IssuanceAcceptanceService acceptance = new IssuanceAcceptanceService(requests);
    public final IssuanceDispatchService dispatch = new IssuanceDispatchService(orders, requests, start, acceptance, failure,
            supplier, 50);
    public final IssuanceTimeoutService timeout = new IssuanceTimeoutService(requests, failure, clock, CALLBACK_TIMEOUT, 50);
    public final IssuanceCallbackService callbacks = new IssuanceCallbackService(signer, callbackLogs,
            new IssuanceCallbackProcessor(callbackLogs, requests, profiles, orders, planQuery, failure, recorder,
                    clock), clock);

    public final ProfileSignalApplier applier = new ProfileSignalApplier(profiles, recorder, clock);
    public final ProfileTrackingService tracking = new ProfileTrackingService(profiles, supplier, applier, clock, 3, 100);
    public final QrViewService qrView = new QrViewService(orders, profiles, recorder, clock);
    public final UsimRegistrationService usim = new UsimRegistrationService(
            new UsimRegistrationStartService(orders, profiles, planQuery, recorder, clock), supplier, applier, profiles);

    /** 결제가 끝난 유심 주문(PICKUP_READY) */
    public Order pickupReadyUsimOrder(OrderNo orderNo) {
        Order order = Order.create(orderNo, UserId.of("user-1"), PlanSnapshot.of(3L, SimType.USIM, USIM_PRICE),
                LocalDate.of(2026, 10, 25), NOW, Duration.ofMinutes(10)).confirmPayment(NOW);
        return orders.save(order);
    }

    /** 발급 실패 → 자동 환불 3회 실패까지 진행해 NEEDS_ATTENTION 주문을 만듦 */
    public void needsAttention() {
        String sup = dispatched();
        sendCallback(issueFailed("sevt_fail", sup));
        RefundResultService results = new RefundResultService(refunds, payments, orders, recorder,
                RetryPolicy.defaults(), clock);
        for (int i = 0; i < 3; i++) {
            results.applyFailure(refunds.findByOrderNo(ORDER_NO).getLast(), "PG down");
        }
    }

    /** 발급 완료(프로필 ISSUED)까지 진행, 여행 시작일 2026-10-25·사용 5일 */
    public void issued() {
        String sup = dispatched();
        sendCallback(issued("sevt_issued", sup));
    }

    /** 결제가 끝난 이심 주문(PAID) + 승인된 결제 */
    public Order paidEsimOrder() {
        Order order = Order.create(ORDER_NO, UserId.of("user-1"), PlanSnapshot.of(1L, SimType.ESIM, PRICE),
                LocalDate.of(2026, 10, 25), NOW, Duration.ofMinutes(10)).confirmPayment(NOW);
        payments.save(Payment.request(ORDER_NO, 1, PRICE, NOW).approve(PgTid.of("pg_1"), PRICE, NOW));
        return orders.save(order);
    }

    public OrderStatus orderStatus() {
        return orders.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    public CallbackResult sendCallback(IssuanceCallbackCommand command) {
        String body = "callback-" + command.eventId() + "-" + command.eventType(); // 서명 대상 원문(내용 무관)
        return callbacks.receive(body, signer.sign(body), raw -> command);
    }

    public IssuanceCallbackCommand issued(String eventId, String supplierOrderNo) {
        return new IssuanceCallbackCommand(eventId, "ISSUED", supplierOrderNo, ORDER_NO.value(), ICCID,
                ACTIVATION_CODE, null);
    }

    public IssuanceCallbackCommand issueFailed(String eventId, String supplierOrderNo) {
        return new IssuanceCallbackCommand(eventId, "ISSUE_FAILED", supplierOrderNo, ORDER_NO.value(), null, null,
                "OUT_OF_STOCK");
    }

    /** 발급 요청까지 진행(접수 번호 SUP-{멱등 키}) */
    public String dispatched() {
        paidEsimOrder();
        dispatch.dispatch();
        return "SUP-" + ORDER_NO.value() + "-ISSUE-1";
    }
}
