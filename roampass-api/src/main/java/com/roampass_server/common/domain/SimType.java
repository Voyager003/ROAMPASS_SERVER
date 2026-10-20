package com.roampass_server.common.domain;

/**
 * The two kinds of SIM the service sells. Shared by plan, order and issuance, so it lives in the
 * shared kernel: the type decides the post-payment path (supplier issuance for eSIM,
 * airport pickup + ICCID registration for USIM).
 */
public enum SimType {
    /** Downloaded to the phone via QR / activation code. */
    ESIM,
    /** Physical card picked up at the airport. */
    USIM
}
