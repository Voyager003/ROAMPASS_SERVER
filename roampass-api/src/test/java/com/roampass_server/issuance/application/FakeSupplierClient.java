package com.roampass_server.issuance.application;

import java.util.ArrayList;
import java.util.List;

import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.issuance.domain.service.SupplierIssueRequest;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;

/**
 * 테스트용 SupplierClient. 결과를 지정하지 않으면 멱등 키 기반 접수 번호로 접수
 */
public class FakeSupplierClient implements SupplierClient {

    private final List<SupplierIssueRequest> requests = new ArrayList<>();
    private SupplierIssueResult nextResult;

    public void willReturn(SupplierIssueResult result) {
        this.nextResult = result;
    }

    @Override
    public SupplierIssueResult requestIssuance(SupplierIssueRequest request) {
        requests.add(request);
        return nextResult != null ? nextResult
                : SupplierIssueResult.accepted(SupplierOrderNo.of("SUP-" + request.idempotencyKey()));
    }

    public List<SupplierIssueRequest> requests() {
        return requests;
    }
}
