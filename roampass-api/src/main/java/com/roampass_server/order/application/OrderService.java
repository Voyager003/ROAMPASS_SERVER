package com.roampass_server.order.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.model.PlanSnapshot;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.order.domain.service.OrderNoGenerator;
import com.roampass_server.plan.application.PlanQueryService;
import com.roampass_server.plan.domain.model.Plan;

/**
 * 주문 생성·조회 유스케이스. 주문 저장과 이력 기록은 같은 트랜잭션
 * 빈 등록은 저장소 구현체가 생기는 영속성 단계(P2-5)에서 추가
 */
public class OrderService {

    private static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";

    private final OrderRepository orderRepository;
    private final OrderNoGenerator orderNoGenerator;
    private final PlanQueryService planQueryService;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;
    private final Duration paymentWindow;

    public OrderService(OrderRepository orderRepository, OrderNoGenerator orderNoGenerator,
                        PlanQueryService planQueryService, StatusHistoryRecorder historyRecorder,
                        Clock clock, Duration paymentWindow) {
        this.orderRepository = orderRepository;
        this.orderNoGenerator = orderNoGenerator;
        this.planQueryService = planQueryService;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
        this.paymentWindow = paymentWindow;
    }

    @Transactional
    public OrderResult createOrder(UserId userId, Long planId, LocalDate travelStartDate) {
        Instant now = Instant.now(clock);
        Plan plan = planQueryService.getPlan(planId);
        OrderNo orderNo = orderNoGenerator.next(now);
        Order order = Order.create(orderNo, userId, PlanSnapshot.of(plan.id(), plan.simType(), plan.price()),
                travelStartDate, now, paymentWindow);

        Order saved = orderRepository.save(order);
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), null, OrderStatus.PENDING,
                Actor.user(userId), "주문 생성", now);
        return OrderResult.of(saved, now);
    }

    /** 본인 주문만 조회. 다른 사용자의 주문은 존재 여부를 드러내지 않도록 없는 주문과 같은 404 */
    @Transactional(readOnly = true)
    public OrderResult getMyOrder(UserId userId, OrderNo orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException(ORDER_NOT_FOUND, "Order not found"));
        return OrderResult.of(order, Instant.now(clock));
    }
}
