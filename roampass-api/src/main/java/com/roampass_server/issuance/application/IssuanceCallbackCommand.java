package com.roampass_server.issuance.application;

/**
 * 공급사 콜백 본문(검증 전 원시 값). 형식 검증은 처리 시점
 * eventType: ISSUED·ISSUE_FAILED(발급 결과), ACTIVATED(개통 이벤트, activatedAt 포함, 유심이면 orderNo 없음)
 */
public record IssuanceCallbackCommand(String eventId, String eventType, String supplierOrderNo, String orderNo,
                                      String iccid, String activationCode, String reason, String activatedAt) {

    /** 발급 결과 콜백(개통 시각 없음) */
    public IssuanceCallbackCommand(String eventId, String eventType, String supplierOrderNo, String orderNo,
                                   String iccid, String activationCode, String reason) {
        this(eventId, eventType, supplierOrderNo, orderNo, iccid, activationCode, reason, null);
    }
}
