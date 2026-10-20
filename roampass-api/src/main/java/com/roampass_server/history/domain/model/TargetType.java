package com.roampass_server.history.domain.model;

/**
 * Kind of aggregate whose status changed.
 */
public enum TargetType {
    ORDER,
    PAYMENT,
    REFUND,
    SIM_PROFILE
}
