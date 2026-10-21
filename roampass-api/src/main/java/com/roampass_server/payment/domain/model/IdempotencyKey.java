package com.roampass_server.payment.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

/**
 * PG 승인 요청마다 함께 보내는 키, 같은 시도를 다시 보내도 중복 승인 방지
 * 결제 시도 1건 = 키 1개: {@code {orderNo}-{attempt}} (예: ORD-20261020-7K3Q9XPA-1)
 * 어느 주문의 몇 번째 시도인지 바로 식별 가능
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

    /** 키에 들어 있는 주문번호({@code {orderNo}-{attempt}}의 앞부분) */
    public OrderNo orderNo() {
        return OrderNo.of(value.substring(0, value.lastIndexOf('-')));
    }
}
