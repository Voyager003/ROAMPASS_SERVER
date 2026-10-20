package com.roampass.mock.supplier.fault;

import org.springframework.http.HttpStatus;

import com.roampass.mock.supplier.issuance.MockSupplierException;

/**
 * 장애 주입 설정 한 벌(부록 A: 발급 실패율 · 콜백 지연 · 콜백 중복/누락 + 서명 오류). 불변, 변경은 통째로 교체
 *
 * @param issueFailureRate   발급 실패 확률(0~1), 실패면 접수 후 실패 콜백
 * @param callbackDelayMs    콜백 추가 지연(0~60000ms), 기본 지연에 더해짐
 * @param callbackDuplicates 같은 콜백(같은 eventId) 추가 발송 횟수(0~10)
 * @param callbackDrop       콜백 누락
 * @param invalidSignature   틀린 비밀키로 서명
 */
public record FaultSettings(double issueFailureRate, long callbackDelayMs, int callbackDuplicates,
                            boolean callbackDrop, boolean invalidSignature) {

    private static final long MAX_DELAY_MS = 60_000;
    private static final int MAX_DUPLICATES = 10;

    public FaultSettings {
        if (!(issueFailureRate >= 0 && issueFailureRate <= 1)) {
            throw invalid("issueFailureRate must be between 0 and 1");
        }
        if (callbackDelayMs < 0 || callbackDelayMs > MAX_DELAY_MS) {
            throw invalid("callbackDelayMs must be between 0 and " + MAX_DELAY_MS);
        }
        if (callbackDuplicates < 0 || callbackDuplicates > MAX_DUPLICATES) {
            throw invalid("callbackDuplicates must be between 0 and " + MAX_DUPLICATES);
        }
    }

    public static FaultSettings none() {
        return new FaultSettings(0, 0, 0, false, false);
    }

    private static MockSupplierException invalid(String message) {
        return new MockSupplierException(HttpStatus.BAD_REQUEST, "INVALID_FAULT_CONFIG", message);
    }
}
