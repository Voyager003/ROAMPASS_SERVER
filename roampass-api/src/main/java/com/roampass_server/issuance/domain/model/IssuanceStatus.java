package com.roampass_server.issuance.domain.model;

/**
 * 발급 요청 상태
 * PENDING: 공급사 접수 응답 전(접수 실패·결과 모름 포함), ACCEPTED: 접수됨·콜백 대기,
 * COMPLETED: 발급 완료 콜백 반영, FAILED: 실패 콜백·접수 거절·콜백 대기 시간 초과
 */
public enum IssuanceStatus {
    PENDING,
    ACCEPTED,
    COMPLETED,
    FAILED;

    public boolean isOpen() {
        return this == PENDING || this == ACCEPTED;
    }
}
