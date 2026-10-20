package com.roampass_server.plan.domain.repository;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.plan.domain.model.Plan;

/**
 * 요금제 검색 조건. 모든 항목 선택, 주어진 항목만 정확히 일치 검색(null이면 조건 없음)
 *
 * @param countryCode ISO 3166-1 alpha-2 대문자
 * @param simType     이심/유심
 * @param days        사용 일수, 1 이상
 * @param dataMb      전체 데이터 용량(MB), 1 이상
 */
public record PlanSearchCondition(String countryCode, SimType simType, Integer days, Integer dataMb) {

    private static final String CODE = "INVALID_PLAN_FILTER";
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    public PlanSearchCondition {
        if (countryCode != null && !COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new InvalidValueException(CODE, "Country code must be two upper-case letters");
        }
        if (days != null && days < 1) {
            throw new InvalidValueException(CODE, "Days must be at least 1");
        }
        if (dataMb != null && dataMb < 1) {
            throw new InvalidValueException(CODE, "Data allowance must be at least 1 MB");
        }
    }

    public static PlanSearchCondition of(String countryCode, SimType simType, Integer days, Integer dataMb) {
        return new PlanSearchCondition(countryCode, simType, days, dataMb);
    }

    public static PlanSearchCondition all() {
        return new PlanSearchCondition(null, null, null, null);
    }

    /** 요금제가 조건에 맞는지 여부. 인메모리 구현과 테스트에서 기준으로 사용 */
    public boolean matches(Plan plan) {
        return (countryCode == null || countryCode.equals(plan.spec().countryCode()))
                && (simType == null || simType == plan.simType())
                && (days == null || days == plan.spec().days())
                && (dataMb == null || dataMb == plan.spec().dataMb());
    }
}
