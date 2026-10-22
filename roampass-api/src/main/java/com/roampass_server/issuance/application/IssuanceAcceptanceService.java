package com.roampass_server.issuance.application;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 공급사 접수 번호 저장(tx). 콜백이 먼저 와서 요청이 이미 끝났으면 변화 없음
 */
@Slf4j
public class IssuanceAcceptanceService {

    private final IssuanceRequestRepository requestRepository;

    public IssuanceAcceptanceService(IssuanceRequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @Transactional
    public void accept(OrderNo orderNo, SupplierOrderNo supplierOrderNo) {
        IssuanceRequest request = requestRepository.findLatestByOrderNo(orderNo).orElseThrow();
        IssuanceRequest accepted;
        try {
            accepted = request.accept(supplierOrderNo);
        } catch (InvalidValueException e) {
            log.error("event=supplier_acceptance_mismatch orderNo={} detail=\"{}\"", orderNo.value(), e.getMessage());
            return;
        }
        if (accepted != request) {
            requestRepository.saveIfStatus(accepted, request.status()); // false면 그 사이 콜백이 먼저 끝냄
        }
    }
}
