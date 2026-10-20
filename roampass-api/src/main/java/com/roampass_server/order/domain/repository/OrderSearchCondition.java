package com.roampass_server.order.domain.repository;

import java.time.Instant;

import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 어드민 주문 검색 조건. 모든 항목 선택(null이면 조건 없음)
 *
 * @param status      저장된 주문 상태
 * @param simType     이심/유심
 * @param createdFrom 생성 시각 하한(포함)
 * @param createdTo   생성 시각 상한(제외)
 */
public record OrderSearchCondition(OrderStatus status, SimType simType, Instant createdFrom, Instant createdTo) {
}
