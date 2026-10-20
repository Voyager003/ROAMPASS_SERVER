package com.roampass_server.plan.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.repository.PlanRepository;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;

/**
 * 테스트용 인메모리 PlanRepository. 저장 순서 유지, 정렬은 하지 않음(정렬 책임은 서비스)
 */
public class FakePlanRepository implements PlanRepository {

    private final Map<Long, Plan> plans = new LinkedHashMap<>();

    public FakePlanRepository add(Plan plan) {
        plans.put(plan.id(), plan);
        return this;
    }

    @Override
    public Optional<Plan> findById(Long id) {
        return Optional.ofNullable(plans.get(id));
    }

    @Override
    public List<Plan> search(PlanSearchCondition condition) {
        List<Plan> result = new ArrayList<>();
        for (Plan plan : plans.values()) {
            if (condition.matches(plan)) {
                result.add(plan);
            }
        }
        return result;
    }
}
