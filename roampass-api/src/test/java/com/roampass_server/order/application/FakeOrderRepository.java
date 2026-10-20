package com.roampass_server.order.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 테스트용 인메모리 OrderRepository. 주문번호를 키로 보관
 */
public class FakeOrderRepository implements OrderRepository {

    protected final Map<OrderNo, Order> orders = new LinkedHashMap<>();

    @Override
    public Order save(Order order) {
        if (orders.containsKey(order.orderNo())) {
            throw new IllegalStateException("duplicate order number"); // 실제 구현의 유니크 제약 위반에 대응
        }
        orders.put(order.orderNo(), order);
        return order;
    }

    @Override
    public Optional<Order> findByOrderNo(OrderNo orderNo) {
        return Optional.ofNullable(orders.get(orderNo));
    }

    public int count() {
        return orders.size();
    }
}
