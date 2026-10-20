package com.roampass_server.issuance.application;

import com.roampass_server.issuance.domain.model.InstallStatus;

/**
 * 유심 ICCID 등록 결과. installStatus가 ISSUED면 개통 결과를 아직 모름(폴링으로 확정)
 */
public record UsimRegistrationResult(String orderNo, String iccid, InstallStatus installStatus) {
}
