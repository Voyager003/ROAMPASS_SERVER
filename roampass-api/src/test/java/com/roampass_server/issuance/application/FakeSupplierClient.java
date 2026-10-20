package com.roampass_server.issuance.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.issuance.domain.service.SupplierIssueRequest;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;

/**
 * 테스트용 SupplierClient. 결과를 지정하지 않으면 멱등 키 기반 접수 번호로 접수, 프로필 조회는 UNKNOWN
 */
public class FakeSupplierClient implements SupplierClient {

    private final List<SupplierIssueRequest> requests = new ArrayList<>();
    private SupplierIssueResult nextResult;
    private final Map<Iccid, SupplierProfileResult> profiles = new HashMap<>();
    private final List<Iccid> profileQueries = new ArrayList<>();

    public synchronized void profileReturns(Iccid iccid, SupplierProfileResult result) {
        profiles.put(iccid, result);
    }

    @Override
    public synchronized SupplierProfileResult getProfile(Iccid iccid) {
        profileQueries.add(iccid);
        return profiles.getOrDefault(iccid, SupplierProfileResult.unknown());
    }

    private final Map<Iccid, SupplierProfileResult> activations = new HashMap<>();

    public synchronized void activationReturns(Iccid iccid, SupplierProfileResult result) {
        activations.put(iccid, result);
    }

    @Override
    public synchronized SupplierProfileResult activateUsim(Iccid iccid) {
        return activations.getOrDefault(iccid, SupplierProfileResult.unknown());
    }

    public synchronized List<Iccid> profileQueries() {
        return profileQueries;
    }

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
