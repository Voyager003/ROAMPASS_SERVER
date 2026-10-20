package com.roampass_server.order.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * Externally visible order number shared with the PG, the supplier and the user.
 * Format: {@code ORD-{KST yyyyMMdd}-{8 upper-case letters/digits}} — the date helps support,
 * the random part makes other people's order numbers hard to guess.
 */
public record OrderNo(String value) {

    private static final Pattern FORMAT = Pattern.compile("ORD-\\d{8}-[A-Z0-9]{8}");

    public OrderNo {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_ORDER_NO", "Order number must look like ORD-yyyyMMdd-XXXXXXXX");
        }
    }

    public static OrderNo of(String value) {
        return new OrderNo(value);
    }
}
