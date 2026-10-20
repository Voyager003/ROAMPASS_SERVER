package com.roampass_server.common.web;

/**
 * A required identification header was not sent. Mapped to 401 by {@link GlobalExceptionHandler}.
 * Lives in the web layer because "a header is missing" is an HTTP concern, not a domain rule.
 */
public class MissingAuthHeaderException extends RuntimeException {

    private final String code;

    public MissingAuthHeaderException(String code, String header) {
        super(header + " header is required");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
