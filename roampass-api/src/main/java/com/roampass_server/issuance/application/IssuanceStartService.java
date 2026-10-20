package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 발급 시작(tx): PAID 주문을 조건부 UPDATE로 ISSUE_REQUESTED로 바꾸고 발급 요청 저장
 * 공급사 호출 전에 커밋해서, 접수 응답보다 콜백이 먼저 와도 요청을 찾을 수 있음
 */
public class IssuanceStartService {

    private final OrderRepository orderRepository;
    private final IssuanceRequestRepository requestRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public IssuanceStartService(OrderRepository orderRepository, IssuanceRequestRepository requestRepository,
                                StatusHistoryRecorder historyRecorder, Clock clock) {
        this.orderRepository = orderRepository;
        this.requestRepository = requestRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /** @return 시작한 발급 요청. 그 사이 다른 실행이 먼저 시작했으면 비어 있음 */
    @Transactional
    public Optional<IssuanceRequest> start(Order order) {
        Instant now = Instant.now(clock);
        if (!orderRepository.saveIfStatus(order.requestIssuance(), OrderStatus.PAID)) {
            return Optional.empty();
        }
        int attempt = requestRepository.countByOrderNo(order.orderNo()) + 1;
        IssuanceRequest saved = requestRepository.save(IssuanceRequest.start(order.orderNo(), attempt, now));
        historyRecorder.record(order.orderNo(), HistoryTarget.order(order.orderNo()), OrderStatus.PAID,
                OrderStatus.ISSUE_REQUESTED, Actor.system(), "발급 요청", now);
        return Optional.of(saved);
    }
}
