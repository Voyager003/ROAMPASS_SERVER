package com.roampass_server.plan.domain.repository;

import java.util.List;
import java.util.Optional;

import com.roampass_server.plan.domain.model.Plan;

/**
 * 요금제 저장소 포트. 구현은 infrastructure 계층
 */
public interface PlanRepository {

    Optional<Plan> findById(Long id);

    /** 조건에 맞는 요금제 목록, 순서는 보장하지 않음(정렬은 호출 쪽 책임) */
    List<Plan> search(PlanSearchCondition condition);
}
