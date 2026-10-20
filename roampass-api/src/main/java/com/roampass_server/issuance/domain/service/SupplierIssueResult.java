package com.roampass_server.issuance.domain.service;

import com.roampass_server.issuance.domain.model.SupplierOrderNo;

/**
 * 공급사 발급 접수 결과(우리 도메인 형식, 공급사 응답 형식과 무관)
 * REJECTED: 공급사가 요청을 거절(4xx), UNKNOWN: 5xx·타임아웃 등 접수 여부를 모름(같은 멱등 키로 재전송)
 */
public record SupplierIssueResult(Outcome outcome, SupplierOrderNo supplierOrderNo, String reason) {

    public enum Outcome {
        ACCEPTED,
        REJECTED,
        UNKNOWN
    }

    public static SupplierIssueResult accepted(SupplierOrderNo supplierOrderNo) {
        return new SupplierIssueResult(Outcome.ACCEPTED, supplierOrderNo, null);
    }

    public static SupplierIssueResult rejected(String reason) {
        return new SupplierIssueResult(Outcome.REJECTED, null, reason);
    }

    public static SupplierIssueResult unknown(String reason) {
        return new SupplierIssueResult(Outcome.UNKNOWN, null, reason);
    }
}
