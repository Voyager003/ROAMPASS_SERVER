package com.roampass_server.payment.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

/**
 * Key sent with each PG approval request so that re-sending the same attempt never approves twice.
 * One payment attempt = one key: {@code {orderNo}-{attempt}} (e.g. ORD-20261020-7K3Q9XPA-1),
 * readable enough to tell which order and which attempt it belongs to.
 */
public record IdempotencyKey(String value) {

    private static final String CODE = "INVALID_IDEMPOTENCY_KEY";
    private static final Pattern FORMAT = Pattern.compile("ORD-\\d{8}-[A-Z0-9]{8}-[1-9]\\d*");

    public IdempotencyKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException(CODE, "Idempotency key must look like {orderNo}-{attempt}");
        }
    }

    public static IdempotencyKey of(OrderNo orderNo, int attempt) {
        if (orderNo == null || attempt < 1) {
            throw new InvalidValueException(CODE, "Idempotency key needs an order number and an attempt number starting at 1");
        }
        return new IdempotencyKey(orderNo.value() + "-" + attempt);
    }

    public static IdempotencyKey from(String value) {
        return new IdempotencyKey(value);
    }
}
