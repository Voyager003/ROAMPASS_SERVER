package com.roampass_server.issuance.domain.service;

import com.roampass_server.common.domain.OrderNo;

public record SupplierIssueRequest(String idempotencyKey, OrderNo orderNo) {
}
