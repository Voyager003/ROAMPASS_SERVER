package com.roampass_server.order.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderIdempotencyKey;
import com.roampass_server.common.domain.SimType;
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
    public boolean insertIfKeyAbsent(Order order) {
        if (order.idempotencyKey() == null) {
            throw new IllegalArgumentException("insertIfKeyAbsent requires an idempotency key");
        }
        return jpaRepository.insertIfKeyAbsent(order.orderNo().value(), order.userId().value(), order.planId(),
                order.simType().name(), order.amount().amount(), order.amount().currency().name(),
                order.travelStartDate(), order.status().name(), order.createdAt(), order.expiresAt(),
                order.issuedAt(), order.idempotencyKey().value()) == 1;
    }

    @Override
    public Optional<Order> findByUserAndIdempotencyKey(UserId userId, OrderIdempotencyKey key) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId.value(), key.value())
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findExpirable(Instant now, int limit) {
        return jpaRepository.findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        OrderStatus.PENDING, now, PageRequest.of(0, limit)).stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    public PageResult<Order> findByUser(UserId userId, Collection<OrderStatus> statuses, int page, int size) {
        Page<OrderJpaEntity> result = jpaRepository.findByUserIdAndStatusIn(userId.value(), statuses,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResult<>(result.getContent().stream().map(OrderJpaEntity::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public List<Order> findByStatuses(Collection<OrderStatus> statuses, int limit) {
        return jpaRepository.findByStatusInOrderByCreatedAtAsc(statuses, PageRequest.of(0, limit)).stream()
                .map(OrderJpaEntity::toDomain).toList();
    }

    @Override
    public List<Order> findIssuable(int limit) {
        return jpaRepository.findByStatusAndSimTypeOrderByCreatedAtAsc(OrderStatus.PAID,
                        SimType.ESIM, PageRequest.of(0, limit)).stream()
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
