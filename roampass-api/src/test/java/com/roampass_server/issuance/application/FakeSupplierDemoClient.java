package com.roampass_server.issuance.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.service.SupplierDemoClient;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;

/**
 * 테스트용 SupplierDemoClient. 기본은 요청한 상태로 바뀐 것처럼 응답, unavailable()이면 실패(UNKNOWN·빈 값)
 */
public class FakeSupplierDemoClient implements SupplierDemoClient {

    private final List<SupplierProfileState> stateRequests = new ArrayList<>();
    private boolean unavailable;
    private String pickupIccid = "8982300900000000016";

    public void unavailable() {
        this.unavailable = true;
    }

    public void pickupCardReturns(String iccid) {
        this.pickupIccid = iccid;
    }

    public List<SupplierProfileState> stateRequests() {
        return stateRequests;
    }

    @Override
    public SupplierProfileResult changeProfileState(Iccid iccid, SupplierProfileState state) {
        stateRequests.add(state);
        if (unavailable) {
            return SupplierProfileResult.unknown();
        }
        return SupplierProfileResult.found(state, state == SupplierProfileState.ENABLED ? IssuanceFixture.NOW : null, 0);
    }

    @Override
    public Optional<String> issuePickupCard(OrderNo orderNo) {
        return unavailable ? Optional.empty() : Optional.of(pickupIccid);
    }
}
