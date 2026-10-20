package com.roampass_server.issuance.application;

/**
 * 공급사 발급 콜백 본문(검증 전 원시 값). 형식 검증은 처리 시점
 */
public record IssuanceCallbackCommand(String eventId, String eventType, String supplierOrderNo, String orderNo,
                                      String iccid, String activationCode, String reason) {
}
