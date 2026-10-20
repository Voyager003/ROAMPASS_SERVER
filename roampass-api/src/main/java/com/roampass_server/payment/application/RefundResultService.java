package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.model.RetryPolicy;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * PG 취소 결과 반영(한 트랜잭션). 환불 요청은 조건부 저장이라 같은 결과가 두 번 반영되지 않음
 * 성공: 환불 SUCCEEDED + 결제 취소 + 주문 REFUNDED. 실패: 다음 시도 예약, 한도 도달 시 GAVE_UP + 주문 NEEDS_ATTENTION
 */
@Slf4j
public class RefundResultService {

    private final RefundAttemptRepository refundAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final RetryPolicy retryPolicy;
    private final Clock clock;

    public RefundResultService(RefundAttemptRepository refundAttemptRepository, PaymentRepository paymentRepository,
                               OrderRepository orderRepository, StatusHistoryRecorder historyRecorder,
                               RetryPolicy retryPolicy, Clock clock) {
        this.refundAttemptRepository = refundAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.historyRecorder = historyRecorder;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
    }

    @Transactional
    public boolean applySuccess(RefundAttempt attempt) {
        Instant now = Instant.now(clock);
        RefundAttempt succeeded = attempt.recordSuccess(now);
        if (!refundAttemptRepository.saveIfPending(succeeded, attempt.attemptCount())) {
            return false;
        }
        OrderNo orderNo = attempt.orderNo();
        historyRecorder.record(orderNo, refundTarget(attempt), RefundStatus.PENDING, RefundStatus.SUCCEEDED,
                Actor.system(), "PG 취소 성공", now);

        Payment payment = paymentRepository.findByIdempotencyKey(attempt.paymentKey()).orElseThrow();
        Payment cancelled = payment.cancel(attempt.amount(), now);
        if (!paymentRepository.saveIfStatus(cancelled, payment.status())) {
            throw new IllegalStateException("Payment changed during refund: " + payment.idempotencyKey().value());
        }
        historyRecorder.record(orderNo, HistoryTarget.of(TargetType.PAYMENT, payment.idempotencyKey().value()),
                payment.status(), cancelled.status(), Actor.system(), "환불 취소", now);

        moveOrder(orderNo, OrderStatus.REFUNDED, "환불 완료", now);
        return true;
    }

    @Transactional
    public boolean applyFailure(RefundAttempt attempt, String error) {
        Instant now = Instant.now(clock);
        RefundAttempt failed = attempt.recordFailure(error, now, retryPolicy);
        if (!refundAttemptRepository.saveIfPending(failed, attempt.attemptCount())) {
            return false;
        }
        if (failed.status() == RefundStatus.GAVE_UP) {
            historyRecorder.record(attempt.orderNo(), refundTarget(attempt), RefundStatus.PENDING,
                    RefundStatus.GAVE_UP, Actor.system(), "PG 취소 " + failed.attemptCount() + "회 실패: " + error, now);
            moveOrder(attempt.orderNo(), OrderStatus.NEEDS_ATTENTION, "자동 환불 포기 — 운영자 확인 필요", now);
        }
        return true;
    }

    private void moveOrder(OrderNo orderNo, OrderStatus target, String reason, Instant now) {
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        if (order.status() != OrderStatus.REFUNDING) {
            log.warn("Order {} is {} while applying refund result, order left unchanged", orderNo.value(),
                    order.status());
            return;
        }
        Order next = target == OrderStatus.REFUNDED ? order.completeRefund() : order.escalate();
        if (!orderRepository.saveIfStatus(next, OrderStatus.REFUNDING)) {
            throw new IllegalStateException("Order changed during refund: " + orderNo.value());
        }
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), OrderStatus.REFUNDING, target, Actor.system(),
                reason, now);
    }

    private static HistoryTarget refundTarget(RefundAttempt attempt) {
        return HistoryTarget.of(TargetType.REFUND, String.valueOf(attempt.id()));
    }
}
