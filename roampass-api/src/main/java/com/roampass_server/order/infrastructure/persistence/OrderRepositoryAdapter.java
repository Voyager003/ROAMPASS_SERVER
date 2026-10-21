package com.roampass_server.order.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.order.domain.repository.OrderSearchCondition;

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
    public PageResult<Order> search(OrderSearchCondition condition, int page, int size) {
        Specification<OrderJpaEntity> spec = (root, query, cb) -> cb.conjunction();
        if (condition.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), condition.status()));
        }
        if (condition.simType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("simType"), condition.simType()));
        }
        if (condition.createdFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), condition.createdFrom()));
        }
        if (condition.createdTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), condition.createdTo()));
        }
        Page<OrderJpaEntity> result = jpaRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResult<>(result.getContent().stream().map(OrderJpaEntity::toDomain).toList(), page, size,
                result.getTotalElements());
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
