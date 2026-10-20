package com.roampass_server.issuance.domain.model;

/**
 * How far an issued SIM has progressed on the user's device. Declared in progression order and only ever
 * moves forward: a signal for an earlier stage arriving late is ignored, and stages may be skipped when a
 * signal is missing (e.g. ISSUED → ACTIVE).
 */
public enum InstallStatus {
    /** Issued; QR / activation code can be shown. */
    ISSUED,
    /** The user opened the QR on our screen — the only signal we record ourselves. */
    QR_VIEWED,
    /** Profile downloaded to the device — known only from the supplier. */
    INSTALLED,
    /** Activated on the local network; the usage period starts. */
    ACTIVE,
    /** Usage period over. */
    EXPIRED;

    public boolean isBeyond(InstallStatus other) {
        return ordinal() > other.ordinal();
    }
}
