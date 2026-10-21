package com.roampass_server.admin.application;

import java.time.Instant;

import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.PaymentStatus;

/**
 * 어드민 주문 검색 조건(과제 2.3-⑤ 결제 상태 / 발급·설치 상태 / SIM 종류 / 기간). 모든 항목 선택(null이면 조건 없음)
 *
 * @param status        저장된 주문 상태(발급 단계 ISSUE_REQUESTED·ISSUED·ISSUE_FAILED 포함)
 * @param paymentStatus 주문의 가장 최근 결제 시도 상태
 * @param installStatus 조회 시점 기준 설치 상태(사용 기간이 지난 ACTIVE는 EXPIRED)
 * @param simType       이심/유심
 * @param createdFrom   생성 시각 하한(포함)
 * @param createdTo     생성 시각 상한(제외)
 */
public record AdminOrderSearchCondition(OrderStatus status, PaymentStatus paymentStatus, InstallStatus installStatus,
                                        SimType simType, Instant createdFrom, Instant createdTo) {

    public static AdminOrderSearchCondition all() {
        return new AdminOrderSearchCondition(null, null, null, null, null, null);
    }
}
