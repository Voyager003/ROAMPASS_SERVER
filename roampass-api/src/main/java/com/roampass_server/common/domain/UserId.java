package com.roampass_server.common.domain;

/**
 * Identifies the end user who sent a request (taken from the X-User-Id header).
 * There is no member domain: the assignment allows header-level identification only.
 */
public record UserId(String value) {

    public UserId {
        IdentifierFormat.require(value, "INVALID_USER_ID", "UserId");
    }

    public static UserId of(String value) {
        return new UserId(value);
    }
}
