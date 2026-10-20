package com.roampass_server.order.api;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * 주문 생성 요청. 여행 시작일 범위 규칙은 도메인 검증(INVALID_TRAVEL_START_DATE)
 */
public record CreateOrderRequest(@NotNull Long planId, @NotNull LocalDate travelStartDate) {
}
