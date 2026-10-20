package com.roampass_server.payment.application;

/**
 * 결제 확정 유스케이스 결과. 정상 흐름의 분기는 예외 대신 결과값
 * (웹훅 처리 트랜잭션 안에서 호출되므로 예외를 던지면 수신 기록까지 롤백)
 */
public enum ConfirmationResult {
    /** 승인 반영, 주문 PAID/PICKUP_READY */
    CONFIRMED,
    /** 만료 후 승인 도착, 주문 REFUNDING */
    LATE_APPROVAL_REFUNDING,
    /** 실패 반영, 주문은 PENDING 유지 */
    FAILED,
    /** 다른 통지가 이미 반영 */
    ALREADY_APPLIED,
    /** 승인 금액 ≠ 결제 금액, 반영 안 함 */
    AMOUNT_MISMATCH,
    PAYMENT_NOT_FOUND,
    /** 현재 상태에 적용할 수 없는 통지(예: 승인된 결제의 실패 통지) */
    IGNORED
}
