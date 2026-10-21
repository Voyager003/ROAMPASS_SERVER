package com.roampass.mock.supplier.issuance;

import java.time.Instant;

/**
 * 공급사 쪽 발급 건 + 프로필. 상태 변경은 synchronized
 */
class SupplierOrder {

    private final String supplierOrderNo;
    private final String orderNo;
    private final String iccid;          // 실패 건이면 null
    private final String activationCode;
    private ProfileState state;
    private Instant activatedAt;
    private long usedMb;

    SupplierOrder(String supplierOrderNo, String orderNo, String iccid, String activationCode) {
        this.supplierOrderNo = supplierOrderNo;
        this.orderNo = orderNo;
        this.iccid = iccid;
        this.activationCode = activationCode;
        this.state = iccid == null ? null : ProfileState.RELEASED;
    }

    String supplierOrderNo() {
        return supplierOrderNo;
    }

    String orderNo() {
        return orderNo;
    }

    String iccid() {
        return iccid;
    }

    String activationCode() {
        return activationCode;
    }

    boolean issued() {
        return iccid != null;
    }

    /** 상태 변경. 처음 ENABLED가 된 순간(개통)인지 함께 돌려줌 → 개통 이벤트 콜백은 그때 1번만 */
    synchronized StateChange changeState(ProfileState next, Instant now) {
        state = next;
        boolean newlyActivated = next == ProfileState.ENABLED && activatedAt == null;
        if (newlyActivated) {
            activatedAt = now;
        }
        return new StateChange(view(), newlyActivated);
    }

    record StateChange(ProfileView view, boolean newlyActivated) {
    }

    synchronized ProfileView view() {
        return new ProfileView(iccid, state, activatedAt, usedMb);
    }
}
