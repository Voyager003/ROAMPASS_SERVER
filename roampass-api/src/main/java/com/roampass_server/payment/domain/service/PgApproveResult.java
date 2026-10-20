package com.roampass_server.payment.domain.service;

import com.roampass_server.common.domain.Money;
import com.roampass_server.payment.domain.model.PgTid;

/**
 * PG 승인 호출 결과
 * UNKNOWN: 타임아웃·연결 실패·5xx 등 결과를 알 수 없음, 결제는 REQUESTED로 두고 웹훅·상태 조회로 확정
 */
public record PgApproveResult(Outcome outcome, PgTid pgTid, Money amount, String failureReason) {

    public enum Outcome {
        APPROVED,
        FAILED,
        UNKNOWN
    }

    public static PgApproveResult approved(PgTid pgTid, Money amount) {
        return new PgApproveResult(Outcome.APPROVED, pgTid, amount, null);
    }

    public static PgApproveResult failed(String reason) {
        return new PgApproveResult(Outcome.FAILED, null, null, reason);
    }

    public static PgApproveResult unknown(String reason) {
        return new PgApproveResult(Outcome.UNKNOWN, null, null, reason);
    }
}
