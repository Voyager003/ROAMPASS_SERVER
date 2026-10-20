package com.roampass_server.admin.application;

import java.time.Instant;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.PaymentStatus;

/**
 * 어드민 주문 목록 한 줄. 결제·설치 상태는 없으면 null(결제 전·발급 전)
 */
public record AdminOrderSummary(String orderNo, String userId, SimType simType, Money amount, OrderStatus orderStatus,
                                PaymentStatus paymentStatus, InstallStatus installStatus, Instant createdAt) {
}
