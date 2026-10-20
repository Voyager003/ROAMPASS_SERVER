package com.roampass_server.issuance.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * Number the supplier returns when it accepts an issuance request; its callback refers to this number.
 */
public record SupplierOrderNo(String value) {

    private static final Pattern NO_WHITESPACE = Pattern.compile("\\S{1,100}");

    public SupplierOrderNo {
        if (value == null || !NO_WHITESPACE.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_SUPPLIER_ORDER_NO", "Supplier order number must be 1-100 non-whitespace characters");
        }
    }

    public static SupplierOrderNo of(String value) {
        return new SupplierOrderNo(value);
    }
}
