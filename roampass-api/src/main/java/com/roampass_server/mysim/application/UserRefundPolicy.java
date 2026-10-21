package com.roampass_server.mysim.application;

import java.util.EnumSet;
import java.util.Set;

import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 사용자 환불 요청 허용 판정
 * <ul>
 *   <li>발급 전(이심 PAID, 유심 PICKUP_READY): 허용</li>
 *   <li>발급 후(ISSUED): 한 번도 설치·개통되지 않은 SIM만 허용</li>
 *   <li>공급사 발급 진행 중(ISSUE_REQUESTED): 거절, 공급사 접수와 환불이 엇갈리는 것 방지</li>
 *   <li>발급 실패·환불 중·조치 필요: 거절, 자동 환불 또는 운영자 조치 대상</li>
 * </ul>
 */
public final class UserRefundPolicy {

    /** 사용자 환불을 받는 주문 상태(ISSUED는 SIM 판정 추가) */
    static final Set<OrderStatus> ALLOWED_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.PICKUP_READY,
            OrderStatus.ISSUED);

    private UserRefundPolicy() {
    }

    /** @param profile 발급 전이면 {@code null} */
    public static boolean isRefundable(Order order, SimProfile profile) {
        if (!ALLOWED_STATUSES.contains(order.status())) {
            return false;
        }
        return order.status() != OrderStatus.ISSUED || (profile != null && profile.isRefundable());
    }
}
