package com.roampass_server.mysim.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;

/**
 * 사용자 환불 시작(한 트랜잭션, 전액). 주문 상태는 조건부 UPDATE로 바꿔 연타·동시 요청에도 1회만 접수
 */
public class MyRefundExecutor {

    static final String REASON = "사용자 환불 요청";

    private final OrderRepository orderRepository;
    private final RefundOpener refundOpener;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public MyRefundExecutor(OrderRepository orderRepository, RefundOpener refundOpener,
                            StatusHistoryRecorder historyRecorder, Clock clock) {
        this.orderRepository = orderRepository;
        this.refundOpener = refundOpener;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /**
     * @param simRefundable ISSUED 주문일 때 공급사 재조회로 확인한 SIM 환불 가능 여부(발급 전이면 true)
     */
    @Transactional
    public MyRefundResult refund(UserId userId, OrderNo orderNo, boolean simRefundable) {
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        if (!UserRefundPolicy.ALLOWED_STATUSES.contains(order.status())) {
            throw new ConflictException("ORDER_NOT_REFUNDABLE", "Order is " + order.status());
        }
        if (order.status() == OrderStatus.ISSUED && !simRefundable) {
            throw new ConflictException("SIM_NOT_REFUNDABLE", "An installed or activated SIM cannot be refunded");
        }
        Order refunding = order.startRefund(simRefundable);
        if (!orderRepository.saveIfStatus(refunding, order.status())) {
            throw new ConflictException("REFUND_CONFLICT", "Order was changed by another request");
        }
        Actor actor = Actor.user(userId);
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), order.status(), OrderStatus.REFUNDING, actor,
                REASON, now);
        refundOpener.open(orderNo, actor, REASON, now);
        return new MyRefundResult(orderNo.value(), OrderStatus.REFUNDING);
    }
}
