package com.roampass_server.issuance.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * SIM card serial number. 19–20 digits whose last digit is a Luhn check digit,
 * so typos when a user types the number printed on a USIM card are caught before calling the supplier.
 */
public record Iccid(String value) {

    private static final String CODE = "INVALID_ICCID";
    private static final Pattern DIGITS = Pattern.compile("\\d{19,20}");

    public Iccid {
        if (value == null || !DIGITS.matcher(value).matches()) {
            throw new InvalidValueException(CODE, "ICCID must be 19 or 20 digits");
        }
        if (!passesLuhn(value)) {
            throw new InvalidValueException(CODE, "ICCID check digit is invalid");
        }
    }

    public static Iccid of(String value) {
        return new Iccid(value);
    }

    // Luhn: from the rightmost digit, double every second digit (subtract 9 if > 9); the sum must end in 0.
    private static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
