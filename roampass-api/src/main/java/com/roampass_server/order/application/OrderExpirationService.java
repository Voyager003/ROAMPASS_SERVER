package com.roampass_server.order.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 결제 대기 만료 유스케이스. 스케줄러가 주기적으로 호출
 * 대상 조회 후 저장 직전까지 다른 쪽(결제 확정)이 상태를 바꿀 수 있으므로 조건부 저장으로 한쪽만 성공
 * 빈 등록은 OrderConfig(설정값 주입)
 */
public class OrderExpirationService {

    private final OrderRepository orderRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;
    private final int batchSize;

    public OrderExpirationService(OrderRepository orderRepository, StatusHistoryRecorder historyRecorder,
                                  Clock clock, int batchSize) {
        this.orderRepository = orderRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * 만료 시각이 지난 PENDING 주문을 최대 묶음 크기만큼 EXPIRED로 변경
     *
     * @return 실제로 만료 처리한 건수(그 사이 상태가 바뀌어 건너뛴 주문 제외)
     */
    @Transactional
    public int expireDue() {
        Instant now = Instant.now(clock);
        int expired = 0;
        for (Order order : orderRepository.findExpirable(now, batchSize)) {
            Order expiredOrder = order.expire(now);
            if (orderRepository.saveIfStatus(expiredOrder, OrderStatus.PENDING)) {
                historyRecorder.record(order.orderNo(), HistoryTarget.order(order.orderNo()), OrderStatus.PENDING,
                        OrderStatus.EXPIRED, Actor.system(), "결제 대기 만료", now);
                expired++;
            }
        }
        return expired;
    }
}
