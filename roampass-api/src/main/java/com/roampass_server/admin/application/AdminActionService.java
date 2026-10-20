package com.roampass_server.admin.application;

import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.application.ProfileSignalApplier;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;

/**
 * 운영자 조치 조율. 트랜잭션 없음
 * 수동 환불 전 SIM이 있으면 공급사 상태를 다시 조회해 반영(트랜잭션 밖): 설치·개통 신호가 늦게 와도
 * 판정 직전의 최신 상태로 "설치·개통된 SIM은 환불 불가"를 지킴
 */
public class AdminActionService {

    private final AdminActionExecutor executor;
    private final SimProfileRepository profileRepository;
    private final SupplierClient supplierClient;
    private final ProfileSignalApplier applier;

    public AdminActionService(AdminActionExecutor executor, SimProfileRepository profileRepository,
                              SupplierClient supplierClient, ProfileSignalApplier applier) {
        this.executor = executor;
        this.profileRepository = profileRepository;
        this.supplierClient = supplierClient;
        this.applier = applier;
    }

    public AdminActionResult refund(AdminId adminId, OrderNo orderNo, String reason) {
        boolean simRefundable = profileRepository.findByOrderNo(orderNo)
                .map(this::refreshed)
                .map(SimProfile::isRefundable)
                .orElse(true);
        return executor.refund(adminId, orderNo, reason, simRefundable);
    }

    public AdminActionResult reissue(AdminId adminId, OrderNo orderNo, String reason) {
        return executor.reissue(adminId, orderNo, reason);
    }

    private SimProfile refreshed(SimProfile profile) {
        applier.apply(profile, supplierClient.getProfile(profile.iccid()));
        return profileRepository.findByOrderNo(profile.orderNo()).orElse(profile);
    }
}
