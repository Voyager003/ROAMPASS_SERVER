package com.roampass_server.admin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.admin.application.AdminOrderDetail.CallbackInfo;
import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.repository.PaymentRepository;

/**
 * 이상 건 조회. "결제됐는데 발급 안 된 주문"을 운영자가 바로 찾을 수 있게 하는 목록
 * 결제 후 미발급은 결제 승인 시각 기준(발급 요청 10초 + 콜백 대기 2분이면 정상적으로 끝났어야 할 시간을 넘긴 것)
 */
@Transactional(readOnly = true)
public class AdminIssueQueryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CallbackLogRepository callbackLogRepository;
    private final AdminOrderQueryService orderQueryService;
    private final Clock clock;
    private final Duration stuckThreshold;
    private final int limit;

    public AdminIssueQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                  CallbackLogRepository callbackLogRepository,
                                  AdminOrderQueryService orderQueryService, Clock clock, Duration stuckThreshold,
                                  int limit) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.callbackLogRepository = callbackLogRepository;
        this.orderQueryService = orderQueryService;
        this.clock = clock;
        this.stuckThreshold = stuckThreshold;
        this.limit = limit;
    }

    public AdminIssues find() {
        Instant threshold = Instant.now(clock).minus(stuckThreshold);
        List<AdminOrderSummary> stuck = orderRepository
                .findByStatuses(EnumSet.of(OrderStatus.PAID, OrderStatus.ISSUE_REQUESTED), limit).stream()
                .filter(order -> approvedAt(order).map(at -> !at.isAfter(threshold)).orElse(false))
                .map(orderQueryService::summarize)
                .toList();
        List<AdminOrderSummary> needsAttention = orderRepository
                .findByStatuses(EnumSet.of(OrderStatus.NEEDS_ATTENTION), limit).stream()
                .map(orderQueryService::summarize)
                .toList();
        return new AdminIssues(stuck, needsAttention, callbacks(CallbackOutcome.AMOUNT_MISMATCH),
                callbacks(CallbackOutcome.SIGNATURE_INVALID), stuckThreshold.toMinutes());
    }

    private Optional<Instant> approvedAt(Order order) {
        return paymentRepository.findByOrderNo(order.orderNo()).stream()
                .map(Payment::approvedAt)
                .filter(at -> at != null)
                .findFirst();
    }

    private List<CallbackInfo> callbacks(CallbackOutcome outcome) {
        return callbackLogRepository.findRecentByOutcome(outcome, limit).stream()
                .map(AdminIssueQueryService::callbackInfo)
                .toList();
    }

    private static CallbackInfo callbackInfo(CallbackLog c) {
        return new CallbackInfo(c.source(), c.eventKey(), c.signatureOk(), c.outcome(), c.note(), c.receivedAt());
    }
}
