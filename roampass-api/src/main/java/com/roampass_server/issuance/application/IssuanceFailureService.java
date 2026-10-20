package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;

/**
 * 발급 실패 처리(한 트랜잭션): 발급 요청 FAILED → 주문 ISSUE_FAILED → REFUNDING → 환불 요청 생성
 * 원인: 공급사 실패 콜백, 접수 거절, 콜백 대기 시간 초과. "결제됐는데 발급 안 됨"이 그대로 남지 않도록 자동 환불까지 연결
 * 발급 요청 상태는 접수 응답(PENDING → ACCEPTED)과 경합할 수 있어 다시 읽고 재시도
 */
public class IssuanceFailureService {

    private static final int ATTEMPTS = 3;

    private final IssuanceRequestRepository requestRepository;
    private final OrderRepository orderRepository;
    private final RefundOpener refundOpener;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public IssuanceFailureService(IssuanceRequestRepository requestRepository, OrderRepository orderRepository,
                                  RefundOpener refundOpener, StatusHistoryRecorder historyRecorder, Clock clock) {
        this.requestRepository = requestRepository;
        this.orderRepository = orderRepository;
        this.refundOpener = refundOpener;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /** @return 실패 처리 여부. 이미 끝난 요청(완료·실패)이면 false */
    @Transactional
    public boolean fail(OrderNo orderNo, String reason, Actor actor) {
        Instant now = Instant.now(clock);
        for (int i = 0; i < ATTEMPTS; i++) {
            Optional<IssuanceRequest> open = requestRepository.findLatestByOrderNo(orderNo)
                    .filter(r -> r.status().isOpen());
            if (open.isEmpty()) {
                return false;
            }
            IssuanceRequest request = open.get();
            if (requestRepository.saveIfStatus(request.fail(reason, now), request.status())) {
                failOrderAndRefund(orderNo, reason, actor, now);
                return true;
            }
        }
        throw new IllegalStateException("Issuance request kept changing: " + orderNo.value());
    }

    private void failOrderAndRefund(OrderNo orderNo, String reason, Actor actor, Instant now) {
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        Order refunding = order.markIssueFailed().startRefund(true);
        if (!orderRepository.saveIfStatus(refunding, OrderStatus.ISSUE_REQUESTED)) {
            throw new IllegalStateException("Order is not ISSUE_REQUESTED: " + orderNo.value());
        }
        HistoryTarget target = HistoryTarget.order(orderNo);
        historyRecorder.record(orderNo, target, OrderStatus.ISSUE_REQUESTED, OrderStatus.ISSUE_FAILED, actor, reason,
                now);
        historyRecorder.record(orderNo, target, OrderStatus.ISSUE_FAILED, OrderStatus.REFUNDING, Actor.system(),
                "발급 실패 — 자동 환불 시작", now);
        refundOpener.open(orderNo, Actor.system(), "발급 실패 자동 환불", now);
    }
}
