package com.roampass_server.payment.domain.service;

/**
 * PG 연동 포트. 구현은 infrastructure/external(HTTP)
 * 네트워크 오류는 예외 대신 {@link PgApproveResult.Outcome#UNKNOWN}으로 반환
 */
public interface PgClient {

    PgApproveResult approve(PgApproveRequest request);
}
