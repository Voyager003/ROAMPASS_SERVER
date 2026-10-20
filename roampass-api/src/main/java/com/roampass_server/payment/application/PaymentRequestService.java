package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.repository.PaymentRepository;

/**
 * 결제 시작(tx#1): 결제 가능 여부 확인 후 REQUESTED 결제 시도 저장
 * PG 호출 전에 커밋해서, 응답보다 웹훅이 먼저 와도 결제를 찾을 수 있음
 */
public class PaymentRequestService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public PaymentRequestService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                 StatusHistoryRecorder historyRecorder, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /**
     * @throws NotFoundException 주문이 없거나 남의 주문(ORDER_NOT_FOUND)
     * @throws ConflictException 결제 대기 상태가 아니거나 만료(ORDER_NOT_PAYABLE),
     *                           결과를 모르는 이전 시도가 남아 있음(PAYMENT_IN_PROGRESS, 이중 결제 방지)
     */
    @Transactional
    public Payment start(UserId userId, OrderNo orderNo) {
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (!order.isPayable(now)) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "Order is " + order.effectiveStatus(now));
        }
        List<Payment> attempts = paymentRepository.findByOrderNo(orderNo);
        if (attempts.stream().anyMatch(p -> p.status() == PaymentStatus.REQUESTED)) {
            throw new ConflictException("PAYMENT_IN_PROGRESS", "A previous payment attempt is still pending");
        }
        Payment payment = paymentRepository.save(
                Payment.request(orderNo, attempts.size() + 1, order.amount(), now));
        historyRecorder.record(orderNo, HistoryTarget.of(TargetType.PAYMENT, payment.idempotencyKey().value()),
                null, PaymentStatus.REQUESTED, Actor.user(userId), "결제 요청", now);
        return payment;
    }
}
