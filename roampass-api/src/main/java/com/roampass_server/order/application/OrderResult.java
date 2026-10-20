package com.roampass_server.order.application;

import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 주문 유스케이스의 결과. 상태는 조회 시점 기준(만료 시각이 지난 PENDING은 EXPIRED)
 */
public record OrderResult(
        OrderNo orderNo,
        Long planId,
        SimType simType,
        Money amount,
        LocalDate travelStartDate,
        OrderStatus status,
        Instant createdAt,
        Instant expiresAt) {

    public static OrderResult of(Order order, Instant now) {
        return new OrderResult(order.orderNo(), order.planId(), order.simType(), order.amount(),
                order.travelStartDate(), order.effectiveStatus(now), order.createdAt(), order.expiresAt());
    }
}
