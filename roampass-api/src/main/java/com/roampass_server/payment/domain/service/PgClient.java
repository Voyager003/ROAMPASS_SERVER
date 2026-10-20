package com.roampass_server.payment.domain.service;

import com.roampass_server.payment.domain.model.IdempotencyKey;

/**
 * PG 연동 포트. 구현은 infrastructure/external(HTTP)
 * 네트워크 오류는 예외 대신 {@link PgApproveResult.Outcome#UNKNOWN}으로 반환
 */
public interface PgClient {

    PgApproveResult approve(PgApproveRequest request);

    /** 멱등 키로 PG의 결제 상태 조회, 응답을 못 받은 승인 요청의 결과 확정용 */
    PgInquiryResult inquire(IdempotencyKey key);

    /** 승인된 결제 취소(전액/부분), 환불 실행용 */
    PgCancelResult cancel(PgCancelRequest request);
}
