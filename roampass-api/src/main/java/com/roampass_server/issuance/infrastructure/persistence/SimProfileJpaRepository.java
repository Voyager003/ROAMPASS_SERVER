package com.roampass_server.issuance.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SimProfileJpaRepository extends JpaRepository<SimProfileJpaEntity, Long> {

    Optional<SimProfileJpaEntity> findByOrderNo(String orderNo);
}
