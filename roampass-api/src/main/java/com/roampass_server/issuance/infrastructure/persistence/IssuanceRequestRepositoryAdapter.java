package com.roampass_server.issuance.infrastructure.persistence;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;

import lombok.RequiredArgsConstructor;

/**
 * IssuanceRequestRepository의 JPA 구현. 상태 변경은 조건부 UPDATE로만 반영
 */
@Repository
@RequiredArgsConstructor
class IssuanceRequestRepositoryAdapter implements IssuanceRequestRepository {

    private final IssuanceRequestJpaRepository jpaRepository;

    @Override
    public IssuanceRequest save(IssuanceRequest request) {
        return jpaRepository.saveAndFlush(IssuanceRequestJpaEntity.fromDomain(request)).toDomain();
    }

    @Override
    public Optional<IssuanceRequest> findLatestByOrderNo(OrderNo orderNo) {
        return jpaRepository.findFirstByOrderNoOrderByAttemptDesc(orderNo.value())
                .map(IssuanceRequestJpaEntity::toDomain);
    }

    @Override
    public int countByOrderNo(OrderNo orderNo) {
        return jpaRepository.countByOrderNo(orderNo.value());
    }

    @Override
    public List<IssuanceRequest> findPending(int limit) {
        return jpaRepository.findByStatusOrderByRequestedAtAsc(IssuanceStatus.PENDING, PageRequest.of(0, limit))
                .stream().map(IssuanceRequestJpaEntity::toDomain).toList();
    }

    @Override
    public List<IssuanceRequest> findOpenRequestedBefore(Instant requestedBefore, int limit) {
        return jpaRepository.findByStatusInAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
                        EnumSet.of(IssuanceStatus.PENDING, IssuanceStatus.ACCEPTED), requestedBefore,
                        PageRequest.of(0, limit))
                .stream().map(IssuanceRequestJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean saveIfStatus(IssuanceRequest request, IssuanceStatus expected) {
        return jpaRepository.updateIfStatus(request.id(), expected, request.status(),
                request.supplierOrderNo() == null ? null : request.supplierOrderNo().value(),
                request.failureReason(), request.completedAt()) == 1;
    }
}
