package com.roampass_server.issuance.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.QrViewService;
import com.roampass_server.issuance.application.UsimRegistrationResult;
import com.roampass_server.issuance.application.UsimRegistrationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 SIM API, X-User-Id 필수. SIM은 주문당 1개라 주문번호로 식별
 */
@RestController
@RequiredArgsConstructor
public class SimController {

    private final QrViewService qrViewService;
    private final UsimRegistrationService usimRegistrationService;

    /** 공항에서 받은 유심 카드의 ICCID 등록 → 공급사 개통 */
    @PostMapping("/api/orders/{orderNo}/usim/register")
    public UsimRegistrationResult registerUsim(UserId userId, @PathVariable String orderNo,
                                               @Valid @RequestBody UsimRegisterRequest request) {
        return usimRegistrationService.register(userId, OrderNo.of(orderNo), request.iccid());
    }

    /** QR 화면을 연 사실 기록. 이미 더 진행된 상태면 변화 없이 204 */
    @PostMapping("/api/sims/{orderNo}/qr-viewed")
    public ResponseEntity<Void> qrViewed(UserId userId, @PathVariable String orderNo) {
        qrViewService.markViewed(userId, OrderNo.of(orderNo));
        return ResponseEntity.noContent().build();
    }
}
