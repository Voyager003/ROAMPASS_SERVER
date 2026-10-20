package com.roampass_server.plan.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, Long>, JpaSpecificationExecutor<PlanJpaEntity> {
}
