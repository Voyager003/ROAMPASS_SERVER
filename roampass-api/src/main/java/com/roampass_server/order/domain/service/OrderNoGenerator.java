package com.roampass_server.order.domain.service;

import java.time.Instant;

import com.roampass_server.common.domain.OrderNo;

/**
 * 새 주문번호 발급 포트. 테스트에서는 고정 번호 주입 가능
 */
public interface OrderNoGenerator {

    OrderNo next(Instant now);
}
