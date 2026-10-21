package com.roampass_server.order.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.order.domain.repository.OrderSearchCondition;

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

    @Override
    public List<Order> findExpirable(Instant now, int limit) {
        return orders.values().stream()
                .filter(o -> o.status() == OrderStatus.PENDING && !now.isBefore(o.expiresAt()))
                .sorted(Comparator.comparing(Order::expiresAt))
                .limit(limit)
                .toList();
    }

    @Override
    public PageResult<Order> search(OrderSearchCondition c, int page, int size) {
        List<Order> matched = orders.values().stream()
                .filter(o -> c.status() == null || o.status() == c.status())
                .filter(o -> c.simType() == null || o.simType() == c.simType())
                .filter(o -> c.createdFrom() == null || !o.createdAt().isBefore(c.createdFrom()))
                .filter(o -> c.createdTo() == null || o.createdAt().isBefore(c.createdTo()))
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
        List<Order> content = matched.stream().skip((long) page * size).limit(size).toList();
        return new PageResult<>(content, page, size, matched.size());
    }

    @Override
    public PageResult<Order> findByUser(UserId userId, Collection<OrderStatus> statuses, int page, int size) {
        List<Order> matched = orders.values().stream()
                .filter(o -> o.isOwnedBy(userId) && statuses.contains(o.status()))
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
        List<Order> content = matched.stream().skip((long) page * size).limit(size).toList();
        return new PageResult<>(content, page, size, matched.size());
    }

    @Override
    public List<Order> findByStatuses(Collection<OrderStatus> statuses, int limit) {
        return orders.values().stream().filter(o -> statuses.contains(o.status()))
                .sorted(Comparator.comparing(Order::createdAt)).limit(limit).toList();
    }

    @Override
    public List<Order> findIssuable(int limit) {
        return orders.values().stream()
                .filter(o -> o.status() == OrderStatus.PAID && o.simType() == SimType.ESIM)
                .sorted(Comparator.comparing(Order::createdAt))
                .limit(limit)
                .toList();
    }

    @Override
    public boolean saveIfStatus(Order order, OrderStatus expected) {
        Order current = orders.get(order.orderNo());
        if (current == null || current.status() != expected) {
            return false; // 그 사이 다른 쪽이 상태를 바꿈
        }
        orders.put(order.orderNo(), order);
        return true;
    }

    /** 테스트에서 저장된 주문을 직접 바꿔 동시 변경을 흉내 낼 때 사용 */
    public void replace(Order order) {
        orders.put(order.orderNo(), order);
    }

    public int count() {
        return orders.size();
    }
}
