package com.roampass_server.mysim.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 내 SIM 상세: 결제 금액 · 주문 상태 · 환불 가능 여부 + 발급된 SIM 정보
 *
 * @param amount     결제 금액(주문 시점 요금제 가격)
 * @param refundable 지금 사용자 환불 요청이 가능한지({@link UserRefundPolicy})
 * @param sim        발급 전이면 null
 * @param waiting    발급 대기 정보, 이심 결제 후 발급 전(PAID·ISSUE_REQUESTED)에만 있음
 */
public record MySimDetail(String orderNo, Long planId, SimType simType, Money amount, LocalDate travelStartDate,
                          OrderStatus orderStatus, Instant createdAt, Instant issuedAt, boolean refundable,
                          SimInfo sim, IssuanceWaiting waiting) {

    /**
     * 발급된 SIM 정보
     *
     * @param activationCode   이심 QR에 담을 활성화 코드, 유심이면 null
     * @param expiresAt        사용 기간 끝(UTC), 개통 전이면 null
     * @param expiresAtKst     사용 기간 끝(KST), 개통 전이면 null
     * @param remainingSeconds 남은 사용 시간(초), 개통 전이면 null, 만료 후 0
     * @param deleted          기기에서 삭제됨
     */
    public record SimInfo(String iccid, String activationCode, InstallStatus installStatus, int usageDays,
                          Instant activatedAt, Instant expiresAt, LocalDateTime expiresAtKst, Long remainingSeconds,
                          boolean deleted) {
    }
}
