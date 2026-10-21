package com.roampass_server.mysim.application;

import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 사용자 환불 요청 결과. 접수되면 REFUNDING(실제 PG 취소는 환불 스케줄러)
 */
public record MyRefundResult(String orderNo, OrderStatus orderStatus) {
}
