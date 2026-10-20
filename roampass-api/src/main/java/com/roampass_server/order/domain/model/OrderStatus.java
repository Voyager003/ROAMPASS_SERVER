package com.roampass_server.order.domain.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 주문의 상태 흐름: 결제부터 SIM을 내주기(또는 환불)까지
 * 이후 기기에서의 SIM 상태(설치, 개통, 만료)는 여기가 아닌 SimProfile이 추적
 * 허용 전이는 아래 한 곳에서 정의
 */
public enum OrderStatus {
    /** 생성됨, expiresAt까지 결제 대기 */
    PENDING,
    /** 결제 없이 결제 대기 시간 경과 */
    EXPIRED,
    /** 결제된 이심 주문, 발급 요청 대기 */
    PAID,
    /** 결제된 유심 주문, 공항 수령과 ICCID 등록 대기 */
    PICKUP_READY,
    /** 공급사에 발급 요청함, 콜백 대기 */
    ISSUE_REQUESTED,
    /** SIM을 내줌(이심 프로필 수신 / 유심 ICCID 등록) */
    ISSUED,
    /** 돈은 받은 상태에서 발급 최종 실패 */
    ISSUE_FAILED,
    /** 환불 진행 중(PG 취소 재시도 가능) */
    REFUNDING,
    /** 환불 완료, 종료 상태 */
    REFUNDED,
    /** 자동 처리 포기, 운영자 조치 필요 */
    NEEDS_ATTENTION;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(PAID, PICKUP_READY, EXPIRED));
        ALLOWED.put(EXPIRED, EnumSet.of(REFUNDING));               // 만료 후 승인 도착
        ALLOWED.put(PAID, EnumSet.of(ISSUE_REQUESTED, REFUNDING));
        ALLOWED.put(PICKUP_READY, EnumSet.of(ISSUED, REFUNDING));
        ALLOWED.put(ISSUE_REQUESTED, EnumSet.of(ISSUED, ISSUE_FAILED));
        ALLOWED.put(ISSUED, EnumSet.of(REFUNDING));                // SIM이 환불 가능한 동안에만
        ALLOWED.put(ISSUE_FAILED, EnumSet.of(REFUNDING));
        ALLOWED.put(REFUNDING, EnumSet.of(REFUNDED, NEEDS_ATTENTION));
        ALLOWED.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(NEEDS_ATTENTION, EnumSet.of(REFUNDING, ISSUE_REQUESTED));
    }

    public boolean canTransitionTo(OrderStatus target) {
        return ALLOWED.get(this).contains(target);
    }
}
