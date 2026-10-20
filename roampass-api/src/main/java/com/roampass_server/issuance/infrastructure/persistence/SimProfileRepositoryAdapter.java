package com.roampass_server.issuance.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * SimProfileRepository의 JPA 구현. 저장은 즉시 INSERT(유니크 위반을 호출 지점에서 드러냄)
 */
@Repository
@RequiredArgsConstructor
class SimProfileRepositoryAdapter implements SimProfileRepository {

    private final SimProfileJpaRepository jpaRepository;

    @Override
    public SimProfile save(SimProfile profile) {
        return jpaRepository.saveAndFlush(SimProfileJpaEntity.fromDomain(profile)).toDomain();
    }

    @Override
    public Optional<SimProfile> findByOrderNo(OrderNo orderNo) {
        return jpaRepository.findByOrderNo(orderNo.value()).map(SimProfileJpaEntity::toDomain);
    }
}
