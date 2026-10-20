package com.roampass_server.common.domain;

/**
 * An amount of money in a single currency, stored in the currency's minor unit.
 * Immutable: every operation returns a new instance. Never negative, so "cancel more than was approved"
 * fails here instead of producing a negative balance. Operations across currencies are rejected.
 */
public record Money(long amount, Currency currency) {

    private static final String INVALID = "INVALID_MONEY";
    private static final String MISMATCH = "CURRENCY_MISMATCH";

    public Money {
        if (currency == null) {
            throw new InvalidValueException(INVALID, "Money currency must not be null");
        }
        if (amount < 0) {
            throw new InvalidValueException(INVALID, "Money amount must not be negative");
        }
    }

    public static Money of(long amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money krw(long amount) {
        return new Money(amount, Currency.KRW);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        try {
            return new Money(Math.addExact(amount, other.amount), currency);
        } catch (ArithmeticException overflow) {
            throw new InvalidValueException(INVALID, "Money amount overflow");
        }
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        // A negative result is rejected by the constructor (e.g. cancelling more than was approved).
        return new Money(amount - other.amount, currency);
    }

    public boolean isGreaterThan(Money other) {
        requireSameCurrency(other);
        return amount > other.amount;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        requireSameCurrency(other);
        return amount >= other.amount;
    }

    private void requireSameCurrency(Money other) {
        if (other.currency != currency) {
            throw new InvalidValueException(MISMATCH,
                    "Cannot combine " + currency + " with " + other.currency);
        }
    }
}
