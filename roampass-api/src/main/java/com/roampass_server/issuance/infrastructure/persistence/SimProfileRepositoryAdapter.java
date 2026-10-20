package com.roampass_server.issuance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.InstallStatus;
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

    @Override
    public List<SimProfile> findTrackable(LocalDate today, int lookaheadDays, int limit) {
        return jpaRepository.findTrackable(today, today.plusDays(lookaheadDays), limit).stream()
                .map(SimProfileJpaEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean saveIfStatus(SimProfile profile, InstallStatus expected) {
        return jpaRepository.updateIfStatus(profile.id(), expected, profile.status(), profile.qrViewedAt(),
                profile.installedAt(), profile.activatedAt(), profile.deletedAt()) == 1;
    }
}
