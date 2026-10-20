package com.roampass_server.payment.domain.service;

import com.roampass_server.common.domain.Money;
import com.roampass_server.payment.domain.model.PgTid;

/**
 * PG 결제 상태 조회 결과
 * APPROVED: 승인됨(이후 취소 여부와 무관), NOT_FOUND: PG가 승인 요청을 받은 적 없음,
 * UNKNOWN: 조회 자체 실패(타임아웃·5xx), 다음에 다시 조회
 */
public record PgInquiryResult(Status status, PgTid pgTid, Money amount) {

    public enum Status {
        APPROVED,
        FAILED,
        NOT_FOUND,
        UNKNOWN
    }

    public static PgInquiryResult approved(PgTid pgTid, Money amount) {
        return new PgInquiryResult(Status.APPROVED, pgTid, amount);
    }

    public static PgInquiryResult failed() {
        return new PgInquiryResult(Status.FAILED, null, null);
    }

    public static PgInquiryResult notFound() {
        return new PgInquiryResult(Status.NOT_FOUND, null, null);
    }

    public static PgInquiryResult unknown() {
        return new PgInquiryResult(Status.UNKNOWN, null, null);
    }
}
