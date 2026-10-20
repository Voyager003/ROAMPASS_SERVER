package com.roampass_server.order.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * OrderRepository의 JPA 구현. 상태 변경은 조건부 UPDATE로만 반영해 동시 변경 시 한쪽만 성공
 */
@Repository
@RequiredArgsConstructor
class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        // saveAndFlush: 주문번호 유니크 위반을 커밋 시점이 아닌 여기서 바로 드러내기 위함
        return jpaRepository.saveAndFlush(OrderJpaEntity.fromDomain(order)).toDomain();
    }

    @Override
    public Optional<Order> findByOrderNo(OrderNo orderNo) {
        return jpaRepository.findByOrderNo(orderNo.value()).map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findExpirable(Instant now, int limit) {
        return jpaRepository.findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        OrderStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean saveIfStatus(Order order, OrderStatus expected) {
        int updated = jpaRepository.updateIfStatus(order.orderNo().value(), expected, order.status(), order.issuedAt());
        return updated == 1;
    }
}
