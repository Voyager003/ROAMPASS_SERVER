package com.roampass_server.plan.api;

import com.roampass_server.common.api.MoneyResponse;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.plan.domain.model.Plan;

public record PlanResponse(Long planId, String countryCode, int days, int dataMb, SimType simType,
                           MoneyResponse price) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.id(), plan.spec().countryCode(), plan.spec().days(), plan.spec().dataMb(),
                plan.simType(), MoneyResponse.from(plan.price()));
    }
}
