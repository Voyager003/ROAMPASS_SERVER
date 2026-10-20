package com.roampass_server.common.domain;

/**
 * 단일 통화의 금액, 통화의 최소 단위로 저장
 * 불변: 모든 연산이 새 인스턴스 반환. 음수 불가라서 "승인 금액보다 많이 취소"는
 * 음수 잔액 대신 여기서 실패. 서로 다른 통화끼리의 연산은 거부
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
        // 음수 결과는 생성자에서 거부(예: 승인 금액보다 많이 취소)
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
