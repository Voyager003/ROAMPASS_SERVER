package com.roampass_server.common.domain.exception;

/**
 * 전이 규칙이 허용하지 않는 상태로의 변경 요청
 */
public class IllegalStateTransitionException extends DomainException {

    private static final String CODE = "ILLEGAL_STATE_TRANSITION";

    public IllegalStateTransitionException(String subject, Object from, Object to) {
        super(CODE, "Cannot transition " + subject + " from " + from + " to " + to);
    }
}
