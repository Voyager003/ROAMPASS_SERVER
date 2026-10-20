package com.roampass_server.plan.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * What a plan offers: where it works, for how many days, and how much data.
 *
 * @param countryCode ISO 3166-1 alpha-2, upper case (e.g. "JP")
 * @param days        number of usage days, at least 1
 * @param dataMb      total data allowance in megabytes, at least 1
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
