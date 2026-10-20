package com.roampass_server.payment.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 결제 시도 1건의 상태 흐름. PG 응답이 유실된 REQUESTED 결제는 상태 조회나 웹훅으로
 * 확정될 때까지 REQUESTED 유지. APPROVED와 FAILED는 서로 뒤집히지 않음
 */
public enum PaymentStatus {
    REQUESTED,
    APPROVED,
    FAILED,
    PARTIALLY_CANCELLED,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED.put(REQUESTED, EnumSet.of(APPROVED, FAILED));
        ALLOWED.put(APPROVED, EnumSet.of(PARTIALLY_CANCELLED, CANCELLED));
        ALLOWED.put(PARTIALLY_CANCELLED, EnumSet.of(PARTIALLY_CANCELLED, CANCELLED)); // 부분 취소 여러 번 허용
        ALLOWED.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(PaymentStatus.class));
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
