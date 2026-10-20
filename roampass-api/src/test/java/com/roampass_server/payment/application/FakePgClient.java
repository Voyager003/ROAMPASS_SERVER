package com.roampass_server.payment.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.service.PgApproveRequest;
import com.roampass_server.payment.domain.service.PgApproveResult;
import com.roampass_server.payment.domain.service.PgClient;
import com.roampass_server.payment.domain.service.PgInquiryResult;

/**
 * 테스트용 PgClient. 다음 승인 결과를 지정하고 받은 요청을 기록
 * 결과를 지정하지 않으면 요청 금액 그대로 승인, 상태 조회는 UNKNOWN
 */
public class FakePgClient implements PgClient {

    private final List<PgApproveRequest> requests = new ArrayList<>();
    private final List<IdempotencyKey> inquiries = new ArrayList<>();
    private final Map<IdempotencyKey, PgInquiryResult> inquiryResults = new HashMap<>();
    private PgApproveResult nextResult;

    public void inquiryReturns(IdempotencyKey key, PgInquiryResult result) {
        inquiryResults.put(key, result);
    }

    @Override
    public PgInquiryResult inquire(IdempotencyKey key) {
        inquiries.add(key);
        return inquiryResults.getOrDefault(key, PgInquiryResult.unknown());
    }

    public List<IdempotencyKey> inquiries() {
        return inquiries;
    }

    public void willReturn(PgApproveResult result) {
        this.nextResult = result;
    }

    @Override
    public PgApproveResult approve(PgApproveRequest request) {
        requests.add(request);
        if (nextResult != null) {
            return nextResult;
        }
        return PgApproveResult.approved(PgTid.of("pg_" + request.idempotencyKey().value()), request.amount());
    }

    public List<PgApproveRequest> requests() {
        return requests;
    }
}
