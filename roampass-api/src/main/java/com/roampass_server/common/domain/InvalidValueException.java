package com.roampass_server.common.domain;

/**
 * A value object or domain input failed validation (e.g. malformed identifier, negative amount).
 */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String code, String message) {
        super(code, message);
    }
}
