package com.roampass_server.common.domain;

/**
 * Identifies the operator who sent an admin request (taken from the X-Admin-Id header).
 * Recorded as the actor of manual actions such as refunds and re-issuance.
 */
public record AdminId(String value) {

    public AdminId {
        IdentifierFormat.require(value, "INVALID_ADMIN_ID", "AdminId");
    }

    public static AdminId of(String value) {
        return new AdminId(value);
    }
}
