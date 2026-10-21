package com.roampass_server.order.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 주문 생성 멱등 키. 클라이언트가 주문 시도마다 만들어 {@code Idempotency-Key} 헤더로 보냄
 * 같은 사용자가 같은 키로 다시 보내면 같은 주문으로 판정(사용자별 유니크)
 * 형식: 영문·숫자·{@code -}·{@code _} 1~64자(UUID 포함)
 */
public record OrderIdempotencyKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    public OrderIdempotencyKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1-64 characters of letters, digits, '-' or '_'");
        }
    }

    public static OrderIdempotencyKey of(String value) {
        return new OrderIdempotencyKey(value);
    }
}
