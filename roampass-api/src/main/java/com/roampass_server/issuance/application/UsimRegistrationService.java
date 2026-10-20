package com.roampass_server.issuance.application;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;

/**
 * 유심 등록 조율. 트랜잭션 없음: 등록(tx) → 공급사 개통 호출(밖) → 결과 반영(tx, 폴링과 같은 반영 로직)
 * 개통 응답을 못 받으면 프로필은 ISSUED로 남고 설치 추적 폴링이 공급사 상태로 확정
 */
public class UsimRegistrationService {

    private final UsimRegistrationStartService startService;
    private final SupplierClient supplierClient;
    private final ProfileSignalApplier applier;
    private final SimProfileRepository profileRepository;

    public UsimRegistrationService(UsimRegistrationStartService startService, SupplierClient supplierClient,
                                   ProfileSignalApplier applier, SimProfileRepository profileRepository) {
        this.startService = startService;
        this.supplierClient = supplierClient;
        this.applier = applier;
        this.profileRepository = profileRepository;
    }

    public UsimRegistrationResult register(UserId userId, OrderNo orderNo, String rawIccid) {
        SimProfile registered = startService.register(userId, orderNo, rawIccid);
        applier.apply(registered, supplierClient.activateUsim(registered.iccid()));
        SimProfile latest = profileRepository.findByOrderNo(orderNo).orElseThrow();
        return new UsimRegistrationResult(orderNo.value(), latest.iccid().value(), latest.status());
    }
}
