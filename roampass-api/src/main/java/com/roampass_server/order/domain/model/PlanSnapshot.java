package com.roampass_server.order.domain.model;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;

/**
 * 주문에 필요한 요금제 정보를 주문 시점에 복사한 값. 주문 도메인은 요금제 도메인을 import하지 않고
 * (도메인끼리는 서로 import 금지), 이후 요금제 가격이 바뀌어도 기존 주문은 그대로 유지
 */
public record PlanSnapshot(Long planId, SimType simType, Money price) {

    public PlanSnapshot {
        if (planId == null || simType == null || price == null) {
            throw new InvalidValueException("INVALID_ORDER", "Plan id, SIM type and price are required");
        }
    }

    public static PlanSnapshot of(Long planId, SimType simType, Money price) {
        return new PlanSnapshot(planId, simType, price);
    }
}
