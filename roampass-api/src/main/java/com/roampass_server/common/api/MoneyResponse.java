package com.roampass_server.common.api;

import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;

/**
 * 금액 응답. amount는 통화 최소 단위 정수(KRW는 원)
 */
public record MoneyResponse(long amount, Currency currency) {

    public static MoneyResponse from(Money money) {
        return new MoneyResponse(money.amount(), money.currency());
    }
}
