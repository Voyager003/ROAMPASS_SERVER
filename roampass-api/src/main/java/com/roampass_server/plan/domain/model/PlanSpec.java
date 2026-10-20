package com.roampass_server.plan.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 요금제가 제공하는 것: 사용 국가, 사용 일수, 데이터 용량
 *
 * @param countryCode ISO 3166-1 alpha-2 대문자(예: "JP")
 * @param days        사용 일수, 1 이상
 * @param dataMb      전체 데이터 용량(MB), 1 이상
 */
public record PlanSpec(String countryCode, int days, int dataMb) {

    private static final String CODE = "INVALID_PLAN";
    private static final Pattern COUNTRY_CODE = Pattern.compile("[A-Z]{2}");

    public PlanSpec {
        if (countryCode == null || !COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new InvalidValueException(CODE, "Country code must be two upper-case letters (ISO 3166-1 alpha-2)");
        }
        if (days < 1) {
            throw new InvalidValueException(CODE, "Plan days must be at least 1");
        }
        if (dataMb < 1) {
            throw new InvalidValueException(CODE, "Plan data allowance must be at least 1 MB");
        }
    }

    public static PlanSpec of(String countryCode, int days, int dataMb) {
        return new PlanSpec(countryCode, days, dataMb);
    }
}
