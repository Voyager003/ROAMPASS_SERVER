package com.roampass_server.plan.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.repository.PlanRepository;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;

import lombok.RequiredArgsConstructor;

/**
 * 요금제 조회 유스케이스. 다른 도메인(주문)도 요금제가 필요하면 이 서비스를 통해 조회
 * 빈 등록(@Service)은 PlanRepository 구현체가 생기는 영속성 단계(P2-5)에서 추가
 */
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanQueryService {

    // 화면에서 같은 국가끼리 짧은 기간·작은 용량부터 보이도록 하는 고정 정렬
    private static final Comparator<Plan> DISPLAY_ORDER = Comparator
            .comparing((Plan p) -> p.spec().countryCode())
            .thenComparingInt(p -> p.spec().days())
            .thenComparingInt(p -> p.spec().dataMb())
            .thenComparing(Plan::id);

    private final PlanRepository planRepository;

    public List<Plan> search(PlanSearchCondition condition) {
        return planRepository.search(condition).stream()
                .sorted(DISPLAY_ORDER)
                .toList();
    }

    public Plan getPlan(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));
    }
}
