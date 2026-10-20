package com.roampass_server.order.domain.model;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;

/**
 * The parts of a plan an order needs, copied at order time. The order domain does not import the
 * plan domain (domains never import each other), and a later price change must not alter existing orders.
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
