package com.roampass_server.admin.application;

import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 운영자 조치 결과. 수동 환불은 REFUNDING(실제 PG 취소는 환불 스케줄러), 재발급은 ISSUE_REQUESTED
 */
public record AdminActionResult(String orderNo, OrderStatus orderStatus) {
}
