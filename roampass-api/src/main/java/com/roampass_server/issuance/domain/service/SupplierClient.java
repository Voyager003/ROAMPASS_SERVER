package com.roampass_server.issuance.domain.service;

/**
 * 공급사 연동 포트. 응답은 공급사 형식이 아닌 우리 도메인 타입(공급사 교체·추가 대비)
 * 네트워크 오류는 예외 대신 {@link SupplierIssueResult.Outcome#UNKNOWN}
 */
public interface SupplierClient {

    SupplierIssueResult requestIssuance(SupplierIssueRequest request);
}
