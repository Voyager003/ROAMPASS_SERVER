package com.roampass_server.plan.domain.model;

import java.util.Objects;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;

/**
 * A plan on sale (aggregate root). Plans are catalog data seeded by migration, so they are only
 * ever reconstituted with an existing id; orders copy the price and SIM type at order time.
 * Equality is by id, as for every aggregate root.
 */
public final class Plan {

    private static final String CODE = "INVALID_PLAN";

    private final Long id;
    private final PlanSpec spec;
    private final SimType simType;
    private final Money price;

    private Plan(Long id, PlanSpec spec, SimType simType, Money price) {
        if (id == null) {
            throw new InvalidValueException(CODE, "Plan id must not be null");
        }
        if (spec == null || simType == null || price == null) {
            throw new InvalidValueException(CODE, "Plan spec, SIM type and price are required");
        }
        if (price.amount() == 0) {
            throw new InvalidValueException(CODE, "Plan price must be greater than zero");
        }
        this.id = id;
        this.spec = spec;
        this.simType = simType;
        this.price = price;
    }

    public static Plan of(Long id, PlanSpec spec, SimType simType, Money price) {
        return new Plan(id, spec, simType, price);
    }

    public Long id() {
        return id;
    }

    public PlanSpec spec() {
        return spec;
    }

    public SimType simType() {
        return simType;
    }

    public Money price() {
        return price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Plan other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
