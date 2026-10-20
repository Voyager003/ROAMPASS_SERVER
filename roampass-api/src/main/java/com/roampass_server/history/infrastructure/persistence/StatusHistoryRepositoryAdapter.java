package com.roampass_server.history.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * StatusHistoryRepository의 JPA 구현
 */
@Repository
@RequiredArgsConstructor
class StatusHistoryRepositoryAdapter implements StatusHistoryRepository {

    private final StatusHistoryJpaRepository jpaRepository;

    @Override
    public void append(StatusHistory history) {
        jpaRepository.save(StatusHistoryJpaEntity.fromDomain(history));
    }

    @Override
    public List<StatusHistory> findByOrderNo(OrderNo orderNo) {
        return jpaRepository.findByOrderNoOrderByOccurredAtAscIdAsc(orderNo.value()).stream()
                .map(StatusHistoryJpaEntity::toDomain)
                .toList();
    }
}
