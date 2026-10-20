package com.roampass_server.order.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 주문 저장소 포트. 구현은 infrastructure 계층
 */
public interface OrderRepository {

    /** 새 주문 저장. 주문번호 중복이면 실패(유니크 제약) */
    Order save(Order order);

    Optional<Order> findByOrderNo(OrderNo orderNo);

    /** PENDING이면서 만료 시각이 {@code now} 이전인 주문, 만료 시각 오름차순 최대 {@code limit}건 */
    List<Order> findExpirable(Instant now, int limit);

    /**
     * 저장된 상태가 아직 {@code expected}일 때만 주문의 변경을 저장(조건부 UPDATE)
     *
     * @return 저장 여부. false면 그 사이 다른 쪽이 상태를 바꾼 것
     */
    boolean saveIfStatus(Order order, OrderStatus expected);
}
