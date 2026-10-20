package com.roampass_server.plan.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.repository.PlanRepository;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;

import lombok.RequiredArgsConstructor;

/**
 * PlanRepository의 JPA 구현. 검색 조건은 주어진 항목만 Specification으로 조합
 * (null 파라미터가 섞인 JPQL은 PostgreSQL에서 타입 추론 문제가 생길 수 있어 회피)
 */
@Repository
@RequiredArgsConstructor
class PlanRepositoryAdapter implements PlanRepository {

    private final PlanJpaRepository jpaRepository;

    @Override
    public Optional<Plan> findById(Long id) {
        return jpaRepository.findById(id).map(PlanJpaEntity::toDomain);
    }

    @Override
    public List<Plan> search(PlanSearchCondition condition) {
        return jpaRepository.findAll(toSpecification(condition)).stream()
                .map(PlanJpaEntity::toDomain)
                .toList();
    }

    private static Specification<PlanJpaEntity> toSpecification(PlanSearchCondition condition) {
        Specification<PlanJpaEntity> spec = (root, query, cb) -> cb.conjunction();
        if (condition.countryCode() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("countryCode"), condition.countryCode()));
        }
        if (condition.simType() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("simType"), condition.simType()));
        }
        if (condition.days() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("days"), condition.days()));
        }
        if (condition.dataMb() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("dataMb"), condition.dataMb()));
        }
        return spec;
    }
}
