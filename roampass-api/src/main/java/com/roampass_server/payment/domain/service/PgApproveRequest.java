package com.roampass_server.payment.domain.service;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;

public record PgApproveRequest(IdempotencyKey idempotencyKey, OrderNo orderNo, Money amount) {
}
