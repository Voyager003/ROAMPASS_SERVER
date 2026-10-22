package com.roampass_server.order.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderIdempotencyKey;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.model.PlanSnapshot;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.order.domain.service.OrderNoGenerator;
import com.roampass_server.plan.application.PlanQueryService;
import com.roampass_server.plan.domain.model.Plan;

import lombok.extern.slf4j.Slf4j;

/**
 * 주문 생성·조회 유스케이스. 주문 저장과 이력 기록은 같은 트랜잭션
 * 빈 등록은 OrderConfig(설정값 주입)
 */
@Slf4j
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
        return createOrder(userId, planId, travelStartDate, null);
    }

    /**
     * 주문 생성. 멱등 키가 있으면 같은 사용자·같은 키의 재전송을 같은 주문으로 판정
     * <ul>
     *   <li>처음: 주문 저장 + 이력 기록</li>
     *   <li>같은 키·같은 내용: 저장하지 않고 처음 만든 주문을 그대로 돌려줌(응답 유실·서버 재시작 뒤 재시도)</li>
     *   <li>같은 키·다른 내용(요금제·여행 시작일): 409 IDEMPOTENCY_KEY_REUSED</li>
     * </ul>
     * 동시 재전송은 DB 유니크 제약(INSERT ... ON CONFLICT DO NOTHING)에서 한쪽만 저장되고, 다른 쪽은 저장된 주문을 읽음
     *
     * @param idempotencyKey 없으면 {@code null}(매번 새 주문)
     */
    @Transactional
    public OrderResult createOrder(UserId userId, Long planId, LocalDate travelStartDate,
                                   OrderIdempotencyKey idempotencyKey) {
        Instant now = Instant.now(clock);
        Plan plan = planQueryService.getPlan(planId);
        OrderNo orderNo = orderNoGenerator.next(now);
        Order order = Order.create(orderNo, userId, PlanSnapshot.of(plan.id(), plan.simType(), plan.price()),
                travelStartDate, now, paymentWindow, idempotencyKey);

        if (idempotencyKey == null) {
            orderRepository.save(order);
        } else if (!orderRepository.insertIfKeyAbsent(order)) {
            return replay(userId, idempotencyKey, planId, travelStartDate, now);
        }
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), null, OrderStatus.PENDING,
                Actor.user(userId), "주문 생성", now);
        return OrderResult.of(order, now);
    }

    private OrderResult replay(UserId userId, OrderIdempotencyKey key, Long planId, LocalDate travelStartDate,
                               Instant now) {
        Order existing = orderRepository.findByUserAndIdempotencyKey(userId, key)
                .orElseThrow(() -> new IllegalStateException("Order for idempotency key disappeared"));
        if (!existing.matchesRequest(planId, travelStartDate)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different order request");
        }
        log.info("event=order_idempotent_replay orderNo={} userId={}", existing.orderNo().value(), userId.value());
        return OrderResult.of(existing, now);
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
