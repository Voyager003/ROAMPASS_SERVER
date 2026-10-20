package com.roampass_server.plan.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.SimType;
import com.roampass_server.plan.application.PlanQueryService;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;

import lombok.RequiredArgsConstructor;

/**
 * 요금제 목록 공개 API, 인증 헤더 불필요
 * 조건 형식 검증은 {@link PlanSearchCondition} 생성 시점(INVALID_PLAN_FILTER)
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanQueryService planQueryService;

    @GetMapping
    public List<PlanResponse> search(@RequestParam(required = false) String country,
                                     @RequestParam(required = false) SimType simType,
                                     @RequestParam(required = false) Integer days,
                                     @RequestParam(required = false) Integer dataMb) {
        PlanSearchCondition condition = PlanSearchCondition.of(country, simType, days, dataMb);
        return planQueryService.search(condition).stream()
                .map(PlanResponse::from)
                .toList();
    }
}
