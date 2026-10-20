package com.roampass_server.issuance.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 공급사가 발급 요청 접수 시 돌려주는 번호. 공급사 콜백이 주문을 가리킬 때 사용
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
