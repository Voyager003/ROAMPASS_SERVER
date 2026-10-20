package com.roampass_server.plan.domain.model;

import java.util.Objects;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;

/**
 * 판매 중인 요금제(집합 루트). 마이그레이션으로 넣는 카탈로그 데이터라서 항상 기존 id로만
 * 복원. 주문은 주문 시점에 가격과 SIM 종류를 복사
 * 다른 집합 루트처럼 동등성은 id 기준
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
