package com.roampass_server.common.domain;

/**
 * A domain object was asked to move to a state that its transition rules do not allow.
 */
public class IllegalStateTransitionException extends DomainException {

    private static final String CODE = "ILLEGAL_STATE_TRANSITION";

    public IllegalStateTransitionException(String subject, Object from, Object to) {
        super(CODE, "Cannot transition " + subject + " from " + from + " to " + to);
    }
}
