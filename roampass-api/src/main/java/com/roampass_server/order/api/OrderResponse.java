package com.roampass_server.order.api;

import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.common.api.MoneyResponse;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.application.OrderResult;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 주문 응답. 시각은 UTC ISO-8601, status는 조회 시점 기준 유효 상태
 */
public record OrderResponse(String orderNo, Long planId, SimType simType, MoneyResponse amount,
                            LocalDate travelStartDate, OrderStatus status, Instant createdAt, Instant expiresAt) {

    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(result.orderNo().value(), result.planId(), result.simType(),
                MoneyResponse.from(result.amount()), result.travelStartDate(), result.status(),
                result.createdAt(), result.expiresAt());
    }
}
