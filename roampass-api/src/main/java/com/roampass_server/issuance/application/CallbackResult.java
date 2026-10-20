package com.roampass_server.issuance.application;

/**
 * 공급사 콜백 처리 결과. DUPLICATE는 같은 이벤트 재수신(기록·반영 모두 생략)
 */
public enum CallbackResult {
    PROCESSED,
    DUPLICATE,
    ALREADY_APPLIED,
    ORDER_NOT_FOUND,
    /** 적용할 수 없는 통지: 이미 실패 처리된 주문의 늦은 성공, 접수 번호 불일치, 모르는 이벤트 */
    IGNORED
}
