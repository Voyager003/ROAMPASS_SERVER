package com.roampass_server.issuance.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * eSIM activation code (the content of the QR code): {@code LPA:1${SM-DP+ address}${matching id}}.
 */
public record ActivationCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("LPA:1\\$[^$\\s]+\\$[^$\\s]+");
    private static final int MAX_LENGTH = 255;

    public ActivationCode {
        if (value == null || value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_ACTIVATION_CODE", "Activation code must look like LPA:1$<smdp>$<matchingId>");
        }
    }

    public static ActivationCode of(String value) {
        return new ActivationCode(value);
    }
}
