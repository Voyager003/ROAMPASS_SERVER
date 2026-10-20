package com.roampass_server.common.domain;

/**
 * Currencies the service can price in. Amounts are stored in the currency's minor unit
 * ({@link #fractionDigits()} = 0 means the major unit, e.g. won).
 * Only KRW is used for selling today; USD exists so the currency guard in {@link Money} is real
 * and testable. Exchange rates and display belong to the multi-currency bonus (B-1).
 */
public enum Currency {
    KRW(0),
    USD(2);

    private final int fractionDigits;

    Currency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public int fractionDigits() {
        return fractionDigits;
    }
}
