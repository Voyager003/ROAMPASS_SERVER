package com.roampass_server.issuance.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * 이심 활성화 코드(QR 코드의 내용): {@code LPA:1${SM-DP+ 주소}${매칭 ID}}
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
