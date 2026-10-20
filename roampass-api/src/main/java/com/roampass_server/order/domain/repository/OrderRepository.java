package com.roampass_server.order.domain.repository;

import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.model.Order;

/**
 * 주문 저장소 포트. 구현은 infrastructure 계층
 */
public interface OrderRepository {

    /** 새 주문 저장. 주문번호 중복이면 실패(유니크 제약) */
    Order save(Order order);

    Optional<Order> findByOrderNo(OrderNo orderNo);
}
