package com.roampass_server.admin.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;

/**
 * 운영자 조치 실행(각각 한 트랜잭션). 주문 상태는 조건부 UPDATE로 바꿔, 두 운영자가 동시에 눌러도 한쪽만 성공
 * 조치마다 이력에 운영자 ID와 사유(필수)를 남김
 */
public class AdminActionExecutor {

    static final Set<OrderStatus> REFUNDABLE = EnumSet.of(OrderStatus.NEEDS_ATTENTION, OrderStatus.PAID,
            OrderStatus.PICKUP_READY, OrderStatus.ISSUED);

    private final OrderRepository orderRepository;
    private final IssuanceRequestRepository issuanceRequestRepository;
    private final RefundOpener refundOpener;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public AdminActionExecutor(OrderRepository orderRepository, IssuanceRequestRepository issuanceRequestRepository,
                               RefundOpener refundOpener, StatusHistoryRecorder historyRecorder, Clock clock) {
        this.orderRepository = orderRepository;
        this.issuanceRequestRepository = issuanceRequestRepository;
        this.refundOpener = refundOpener;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /**
     * 수동 환불 시작(전액). {@code simRefundable}은 ISSUED 주문일 때 공급사 재조회로 확인한 SIM 환불 가능 여부
     */
    @Transactional
    public AdminActionResult refund(AdminId adminId, OrderNo orderNo, String reason, boolean simRefundable) {
        requireReason(reason);
        Instant now = Instant.now(clock);
        Order order = find(orderNo);
        if (!REFUNDABLE.contains(order.status())) {
            throw new ConflictException("ORDER_NOT_REFUNDABLE", "Order is " + order.status());
        }
        if (order.status() == OrderStatus.ISSUED && !simRefundable) {
            throw new ConflictException("SIM_NOT_REFUNDABLE", "An installed or activated SIM cannot be refunded");
        }
        Order refunding = order.startRefund(simRefundable);
        if (!orderRepository.saveIfStatus(refunding, order.status())) {
            throw new ConflictException("ACTION_CONFLICT", "Order was changed by another action");
        }
        Actor actor = Actor.admin(adminId);
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), order.status(), OrderStatus.REFUNDING, actor,
                reason, now);
        refundOpener.open(orderNo, actor, reason, now);
        return new AdminActionResult(orderNo.value(), OrderStatus.REFUNDING);
    }

    /** 재발급: 한 번도 발급된 적 없는 NEEDS_ATTENTION 이심 주문만(도메인 규칙). 새 발급 요청은 발급 스케줄러가 전송 */
    @Transactional
    public AdminActionResult reissue(AdminId adminId, OrderNo orderNo, String reason) {
        requireReason(reason);
        Instant now = Instant.now(clock);
        Order order = find(orderNo);
        if (order.status() != OrderStatus.NEEDS_ATTENTION) {
            throw new ConflictException("ORDER_NOT_REISSUABLE", "Order is " + order.status());
        }
        Order reissued;
        try {
            reissued = order.reissue();
        } catch (IllegalStateTransitionException e) {
            throw new ConflictException("ORDER_NOT_REISSUABLE", e.getMessage());
        }
        if (!orderRepository.saveIfStatus(reissued, OrderStatus.NEEDS_ATTENTION)) {
            throw new ConflictException("ACTION_CONFLICT", "Order was changed by another action");
        }
        int attempt = issuanceRequestRepository.countByOrderNo(orderNo) + 1;
        issuanceRequestRepository.save(IssuanceRequest.start(orderNo, attempt, now));
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), OrderStatus.NEEDS_ATTENTION,
                OrderStatus.ISSUE_REQUESTED, Actor.admin(adminId), reason, now);
        return new AdminActionResult(orderNo.value(), OrderStatus.ISSUE_REQUESTED);
    }

    private Order find(OrderNo orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    // 이력 기록 시점에도 도메인이 강제하지만, 상태를 바꾸기 전에 거절
    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("ADMIN_REASON_REQUIRED",
                    "Operator actions must state a reason");
        }
    }
}
