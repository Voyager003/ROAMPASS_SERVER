package com.roampass_server.payment.domain.model;

/**
 * 환불 요청 1건의 상태
 */
public enum RefundStatus {
    /** 다음 PG 취소 시도(nextRetryAt) 대기 중 */
    PENDING,
    /** PG 취소 확인, 종료 상태 */
    SUCCEEDED,
    /** 재시도 모두 소진, 운영자 확인 필요. 종료 상태 — 수동 환불은 새 요청으로 시작 */
    GAVE_UP
}
