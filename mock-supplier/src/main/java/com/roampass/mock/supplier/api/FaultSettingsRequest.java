package com.roampass.mock.supplier.api;

import com.roampass.mock.supplier.fault.FaultSettings;

/**
 * 장애 설정 전체 교체 요청. 빠진 항목(null)은 0/false(장애 없음)
 * 원시 타입이면 빠진 항목에서 본문 파싱이 실패하므로 래퍼 타입 사용(decisions #80)
 */
public record FaultSettingsRequest(Double issueFailureRate, Long callbackDelayMs, Integer callbackDuplicates,
                                   Boolean callbackDrop, Boolean invalidSignature) {

    FaultSettings toSettings() {
        return new FaultSettings(issueFailureRate == null ? 0 : issueFailureRate,
                callbackDelayMs == null ? 0 : callbackDelayMs, callbackDuplicates == null ? 0 : callbackDuplicates,
                Boolean.TRUE.equals(callbackDrop), Boolean.TRUE.equals(invalidSignature));
    }
}
