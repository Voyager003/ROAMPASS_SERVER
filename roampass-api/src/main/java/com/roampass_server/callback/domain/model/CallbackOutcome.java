package com.roampass_server.callback.domain.model;

/**
 * 외부 통지 처리 결과
 */
public enum CallbackOutcome {
    /** 기록만 된 상태, 처리 중 */
    RECEIVED,
    /** 상태 변경까지 반영 */
    PROCESSED,
    /** 다른 경로(응답·다른 이벤트)로 이미 반영된 내용 */
    ALREADY_APPLIED,
    /** 금액 불일치로 거절, 이상 건 */
    AMOUNT_MISMATCH,
    /** 대상(결제 등)을 찾지 못함 */
    TARGET_NOT_FOUND,
    /** 현재 상태에 적용할 수 없거나 모르는 이벤트, 이상 건 */
    IGNORED,
    /** 서명 불일치로 거절 */
    SIGNATURE_INVALID
}
