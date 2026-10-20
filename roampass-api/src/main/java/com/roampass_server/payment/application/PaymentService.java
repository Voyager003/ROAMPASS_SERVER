package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.service.PgApproveRequest;
import com.roampass_server.payment.domain.service.PgApproveResult;
import com.roampass_server.payment.domain.service.PgClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 요청 조율. 트랜잭션 없음
 * tx#1 결제 시작 → PG 호출(트랜잭션 밖, DB 커넥션을 잡고 기다리지 않음) → tx#2 결과 반영
 */
@Slf4j
public class PaymentService {

    private final PaymentRequestService requestService;
    private final PaymentConfirmationService confirmationService;
    private final PgClient pgClient;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final Clock clock;

    public PaymentService(PaymentRequestService requestService, PaymentConfirmationService confirmationService,
                          PgClient pgClient, PaymentRepository paymentRepository, OrderRepository orderRepository,
                          Clock clock) {
        this.requestService = requestService;
        this.confirmationService = confirmationService;
        this.pgClient = pgClient;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    public PayResult pay(UserId userId, OrderNo orderNo) {
        Payment payment = requestService.start(userId, orderNo);
        PgApproveResult result = pgClient.approve(
                new PgApproveRequest(payment.idempotencyKey(), orderNo, payment.amount()));
        switch (result.outcome()) {
            case APPROVED -> confirmationService.confirmApproval(payment.idempotencyKey(), result.pgTid(),
                    result.amount(), Actor.pg());
            case FAILED -> confirmationService.confirmFailure(payment.idempotencyKey(), result.failureReason(),
                    Actor.pg());
            case UNKNOWN -> log.warn("PG result unknown for {}: {}", payment.idempotencyKey().value(),
                    result.failureReason());
        }
        Payment latest = paymentRepository.findByIdempotencyKey(payment.idempotencyKey()).orElseThrow();
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        return new PayResult(orderNo, latest.idempotencyKey(), latest.status(),
                order.effectiveStatus(Instant.now(clock)));
    }
}
