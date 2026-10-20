package com.roampass_server.common.domain;

/**
 * Base class for business-rule violations raised by domain objects.
 * Pure Java on purpose: it carries a stable error {@code code} but no HTTP status.
 * The web layer decides how each subtype maps to an HTTP response.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
