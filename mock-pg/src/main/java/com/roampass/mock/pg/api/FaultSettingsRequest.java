package com.roampass.mock.pg.api;

import com.roampass.mock.pg.fault.FaultSettings;

/**
 * 장애 설정 전체 교체 요청. 빠진 항목(null)은 0/false(장애 없음)
 * 원시 타입이면 빠진 항목에서 본문 파싱이 실패하므로 래퍼 타입 사용
 * 범위 검증은 {@link FaultSettings} 생성 시점(INVALID_FAULT_CONFIG)
 */
public record FaultSettingsRequest(Double approveFailureRate, Long responseDelayMs, Integer webhookDuplicates,
                                   Boolean webhookDrop, Long webhookAmountDelta, Double cancelFailureRate) {

    FaultSettings toSettings() {
        return new FaultSettings(orZero(approveFailureRate), orZero(responseDelayMs), orZero(webhookDuplicates),
                Boolean.TRUE.equals(webhookDrop), orZero(webhookAmountDelta), orZero(cancelFailureRate));
    }

    private static double orZero(Double value) {
        return value == null ? 0 : value;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
