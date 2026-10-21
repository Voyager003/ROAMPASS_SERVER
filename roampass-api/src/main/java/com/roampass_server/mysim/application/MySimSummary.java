package com.roampass_server.mysim.application;

import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 내 SIM 목록 한 줄. 설치 상태는 발급 전이면 null
 */
public record MySimSummary(String orderNo, Long planId, SimType simType, Money amount, LocalDate travelStartDate,
                           OrderStatus orderStatus, InstallStatus installStatus, Instant createdAt) {
}
