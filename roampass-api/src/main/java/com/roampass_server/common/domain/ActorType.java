package com.roampass_server.common.domain;

/**
 * Who (or what signal) caused a change.
 */
public enum ActorType {
    /** The end user (X-User-Id). */
    USER,
    /** An operator (X-Admin-Id). */
    ADMIN,
    /** Our own scheduler or automatic handling. */
    SYSTEM,
    /** The payment gateway (approval response, webhook). */
    PG,
    /** The SIM supplier (callback, status lookup). */
    SUPPLIER
}
