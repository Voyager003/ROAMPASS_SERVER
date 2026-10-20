package com.roampass_server.payment.domain.service;

/**
 * PG 취소 결과. FAILED·UNKNOWN 모두 같은 cancelKey로 재시도(PG 쪽 취소 멱등)
 */
public record PgCancelResult(Outcome outcome, String reason) {

    public enum Outcome {
        CANCELLED,
        FAILED,
        UNKNOWN
    }

    public static PgCancelResult cancelled() {
        return new PgCancelResult(Outcome.CANCELLED, null);
    }

    public static PgCancelResult failed(String reason) {
        return new PgCancelResult(Outcome.FAILED, reason);
    }

    public static PgCancelResult unknown(String reason) {
        return new PgCancelResult(Outcome.UNKNOWN, reason);
    }
}
