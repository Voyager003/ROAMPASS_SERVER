package com.roampass_server.issuance.domain.service;

import java.time.Instant;

/**
 * 공급사 프로필 상태 조회 결과
 * NOT_FOUND: 공급사가 모르는 ICCID, UNKNOWN: 조회 실패(타임아웃·5xx), 다음 주기에 다시 조회
 */
public record SupplierProfileResult(Outcome outcome, SupplierProfileState state, Instant activatedAt, long usedMb) {

    public enum Outcome {
        FOUND,
        NOT_FOUND,
        UNKNOWN
    }

    public static SupplierProfileResult found(SupplierProfileState state, Instant activatedAt, long usedMb) {
        return new SupplierProfileResult(Outcome.FOUND, state, activatedAt, usedMb);
    }

    public static SupplierProfileResult notFound() {
        return new SupplierProfileResult(Outcome.NOT_FOUND, null, null, 0);
    }

    public static SupplierProfileResult unknown() {
        return new SupplierProfileResult(Outcome.UNKNOWN, null, null, 0);
    }
}
