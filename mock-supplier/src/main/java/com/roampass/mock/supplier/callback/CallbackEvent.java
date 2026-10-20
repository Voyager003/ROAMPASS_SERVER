package com.roampass.mock.supplier.callback;

import java.time.Instant;

/**
 * 콜백 본문(부록 A + eventId·orderNo·eventType 추가)
 * eventId는 이벤트마다 고유, 재발송도 같은 값(수신 측 중복 판정 키)
 * orderNo는 수신 측이 접수 번호를 저장하기 전에 콜백이 도착해도 주문을 찾을 수 있게 포함
 */
public record CallbackEvent(String eventId, CallbackEventType eventType, String supplierOrderNo, String orderNo,
                            String iccid, String activationCode, String reason, Instant occurredAt) {
}
