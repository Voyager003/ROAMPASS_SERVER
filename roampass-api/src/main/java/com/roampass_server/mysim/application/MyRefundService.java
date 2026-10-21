package com.roampass_server.mysim.application;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.ProfileSignalApplier;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 사용자 환불 요청 조율. 트랜잭션 없음
 * 본인 주문 확인 후, SIM이 있으면 공급사 상태를 다시 조회해 반영(트랜잭션 밖)하고 판정
 * — 폴링 주기 사이에 설치·개통된 SIM을 환불하지 않기 위함(운영자 수동 환불과 같은 방식)
 */
public class MyRefundService {

    private final MyRefundExecutor executor;
    private final OrderRepository orderRepository;
    private final SimProfileRepository profileRepository;
    private final SupplierClient supplierClient;
    private final ProfileSignalApplier applier;

    public MyRefundService(MyRefundExecutor executor, OrderRepository orderRepository,
                           SimProfileRepository profileRepository, SupplierClient supplierClient,
                           ProfileSignalApplier applier) {
        this.executor = executor;
        this.orderRepository = orderRepository;
        this.profileRepository = profileRepository;
        this.supplierClient = supplierClient;
        this.applier = applier;
    }

    public MyRefundResult refund(UserId userId, OrderNo orderNo) {
        orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        boolean simRefundable = profileRepository.findByOrderNo(orderNo)
                .map(this::refreshed)
                .map(SimProfile::isRefundable)
                .orElse(true);
        return executor.refund(userId, orderNo, simRefundable);
    }

    private SimProfile refreshed(SimProfile profile) {
        applier.apply(profile, supplierClient.getProfile(profile.iccid()));
        return profileRepository.findByOrderNo(profile.orderNo()).orElse(profile);
    }
}
