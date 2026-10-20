package com.roampass_server.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

import lombok.RequiredArgsConstructor;

/**
 * RefundAttemptRepository의 JPA 구현. 결과 반영은 조건부 UPDATE로 한 번만
 */
@Repository
@RequiredArgsConstructor
class RefundAttemptRepositoryAdapter implements RefundAttemptRepository {

    private final RefundAttemptJpaRepository jpaRepository;

    @Override
    public RefundAttempt save(RefundAttempt attempt) {
        return jpaRepository.saveAndFlush(RefundAttemptJpaEntity.fromDomain(attempt)).toDomain();
    }

    @Override
    public List<RefundAttempt> findDue(Instant now, int limit) {
        return jpaRepository.findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(RefundStatus.PENDING, now,
                PageRequest.of(0, limit)).stream().map(RefundAttemptJpaEntity::toDomain).toList();
    }

    @Override
    public List<RefundAttempt> findByOrderNo(OrderNo orderNo) {
        return jpaRepository.findByOrderNoOrderByCreatedAtAsc(orderNo.value()).stream()
                .map(RefundAttemptJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean saveIfPending(RefundAttempt attempt, int expectedAttemptCount) {
        return jpaRepository.updateIfPending(attempt.id(), expectedAttemptCount, attempt.status(),
                attempt.attemptCount(), attempt.nextRetryAt(), attempt.lastError(), attempt.completedAt()) == 1;
    }
}
