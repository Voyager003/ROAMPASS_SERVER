package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentAmountMismatchException;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.repository.PaymentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 확정(tx#2) 단일 유스케이스. 승인 응답·웹훅·상태 조회 모든 경로가 이 코드로 반영
 * 중복 통지 방어 3단: 도메인 멱등(같은 거래 ID면 no-op) → 결제 조건부 UPDATE(REQUESTED일 때만)
 * → 주문 조건부 UPDATE(읽은 상태 그대로일 때만). 동시 통지 중 한쪽만 상태 변경·이력 기록
 */
@Slf4j
public class PaymentConfirmationService {

    private static final int ORDER_UPDATE_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public PaymentConfirmationService(PaymentRepository paymentRepository, OrderRepository orderRepository,
                                      StatusHistoryRecorder historyRecorder, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    @Transactional
    public ConfirmationResult confirmApproval(IdempotencyKey key, PgTid pgTid, Money approvedAmount, Actor actor) {
        Instant now = Instant.now(clock);
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (payment == null) {
            log.warn("Approval for unknown payment {}", key.value());
            return ConfirmationResult.PAYMENT_NOT_FOUND;
        }
        Payment approved;
        try {
            approved = payment.approve(pgTid, approvedAmount, now);
        } catch (PaymentAmountMismatchException e) {
            log.warn("Approval amount mismatch {}: expected {}, got {}", key.value(), payment.amount(), approvedAmount);
            return ConfirmationResult.AMOUNT_MISMATCH;
        } catch (IllegalStateTransitionException e) {
            log.warn("Approval not applicable to payment {}: {}", key.value(), e.getMessage());
            return ConfirmationResult.IGNORED;
        }
        if (approved == payment || !paymentRepository.saveIfStatus(approved, payment.status())) {
            return ConfirmationResult.ALREADY_APPLIED;
        }
        historyRecorder.record(payment.orderNo(), paymentTarget(key), payment.status(), PaymentStatus.APPROVED,
                actor, "결제 승인", now);
        return applyToOrder(payment.orderNo(), actor, now);
    }

    @Transactional
    public ConfirmationResult confirmFailure(IdempotencyKey key, String reason, Actor actor) {
        Instant now = Instant.now(clock);
        Payment payment = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (payment == null) {
            log.warn("Failure for unknown payment {}", key.value());
            return ConfirmationResult.PAYMENT_NOT_FOUND;
        }
        if (payment.status() == PaymentStatus.FAILED) {
            return ConfirmationResult.ALREADY_APPLIED;
        }
        Payment failed;
        try {
            failed = payment.fail(reason, now);
        } catch (IllegalStateTransitionException e) {
            log.warn("Failure not applicable to payment {}: {}", key.value(), e.getMessage());
            return ConfirmationResult.IGNORED; // 승인된 결제는 뒤집지 않음
        }
        if (!paymentRepository.saveIfStatus(failed, payment.status())) {
            return ConfirmationResult.ALREADY_APPLIED;
        }
        historyRecorder.record(payment.orderNo(), paymentTarget(key), payment.status(), PaymentStatus.FAILED,
                actor, "결제 실패: " + reason, now);
        return ConfirmationResult.FAILED;
    }

    // 읽은 뒤 저장 전에 만료 스케줄러가 PENDING → EXPIRED로 바꿀 수 있어 다시 읽고 재시도
    private ConfirmationResult applyToOrder(OrderNo orderNo, Actor actor, Instant now) {
        for (int attempt = 0; attempt < ORDER_UPDATE_ATTEMPTS; attempt++) {
            Order order = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new IllegalStateException("Order missing for payment: " + orderNo.value()));
            OrderStatus stored = order.status();
            Order next = order.confirmPayment(now);
            if (orderRepository.saveIfStatus(next, stored)) {
                recordOrderTransition(orderNo, stored, next.status(), actor, now);
                return next.status() == OrderStatus.REFUNDING
                        ? ConfirmationResult.LATE_APPROVAL_REFUNDING : ConfirmationResult.CONFIRMED;
            }
        }
        throw new IllegalStateException("Order kept changing while confirming payment: " + orderNo.value());
    }

    private void recordOrderTransition(OrderNo orderNo, OrderStatus from, OrderStatus to, Actor actor, Instant now) {
        HistoryTarget target = HistoryTarget.order(orderNo);
        if (to != OrderStatus.REFUNDING) {
            historyRecorder.record(orderNo, target, from, to, actor, "결제 확정", now);
            return;
        }
        if (from == OrderStatus.PENDING) {
            historyRecorder.record(orderNo, target, OrderStatus.PENDING, OrderStatus.EXPIRED, Actor.system(),
                    "결제 대기 만료", now);
        }
        historyRecorder.record(orderNo, target, OrderStatus.EXPIRED, OrderStatus.REFUNDING, actor,
                "만료 후 승인 도착 — 자동 환불", now);
    }

    private static HistoryTarget paymentTarget(IdempotencyKey key) {
        return HistoryTarget.of(TargetType.PAYMENT, key.value());
    }
}
