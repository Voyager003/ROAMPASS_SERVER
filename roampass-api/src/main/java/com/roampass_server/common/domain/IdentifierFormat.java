package com.roampass_server.common.domain;

import java.util.regex.Pattern;

/**
 * Shared validation for identifiers that arrive in request headers.
 * These values end up in status history and logs, so only a narrow character set is accepted,
 * and error messages never echo the raw input (prevents log injection).
 */
final class IdentifierFormat {

    static final int MAX_LENGTH = 64;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]+");

    private IdentifierFormat() {
    }

    static String require(String value, String errorCode, String label) {
        if (value == null || value.isEmpty()) {
            throw new InvalidValueException(errorCode, label + " must not be empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidValueException(errorCode, label + " must be at most " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidValueException(errorCode, label + " may contain only letters, digits, '_' and '-'");
        }
        return value;
    }
}
