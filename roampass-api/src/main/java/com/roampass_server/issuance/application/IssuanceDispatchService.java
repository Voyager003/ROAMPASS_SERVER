package com.roampass_server.issuance.application;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.issuance.domain.service.SupplierIssueRequest;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 서버 주도 발급. 스케줄러가 주기적으로 호출, 트랜잭션 없음(공급사 호출은 밖)
 * 결제 확정 뒤의 발급은 클라이언트 호출에 의존하지 않아 사용자가 브라우저를 닫아도 진행
 * 1) 접수 응답을 못 받은 요청은 같은 멱등 키로 재전송 2) PAID 이심 주문의 발급 시작
 */
@Slf4j
public class IssuanceDispatchService {

    private final OrderRepository orderRepository;
    private final IssuanceRequestRepository requestRepository;
    private final IssuanceStartService startService;
    private final IssuanceAcceptanceService acceptanceService;
    private final IssuanceFailureService failureService;
    private final SupplierClient supplierClient;
    private final int batchSize;

    public IssuanceDispatchService(OrderRepository orderRepository, IssuanceRequestRepository requestRepository,
                                   IssuanceStartService startService, IssuanceAcceptanceService acceptanceService,
                                   IssuanceFailureService failureService, SupplierClient supplierClient,
                                   int batchSize) {
        this.orderRepository = orderRepository;
        this.requestRepository = requestRepository;
        this.startService = startService;
        this.acceptanceService = acceptanceService;
        this.failureService = failureService;
        this.supplierClient = supplierClient;
        this.batchSize = batchSize;
    }

    /** @return 새로 시작한 발급 건수 */
    public int dispatch() {
        for (IssuanceRequest pending : requestRepository.findPending(batchSize)) {
            send(pending);
        }
        int started = 0;
        for (Order order : orderRepository.findIssuable(batchSize)) {
            var request = startService.start(order);
            if (request.isPresent()) {
                send(request.get());
                started++;
            }
        }
        return started;
    }

    private void send(IssuanceRequest request) {
        SupplierIssueResult result = supplierClient.requestIssuance(
                new SupplierIssueRequest(request.idempotencyKey(), request.orderNo()));
        switch (result.outcome()) {
            case ACCEPTED -> acceptanceService.accept(request.orderNo(), result.supplierOrderNo());
            case REJECTED -> failureService.fail(request.orderNo(), "공급사 접수 거절: " + result.reason(),
                    Actor.supplier());
            case UNKNOWN -> log.warn("event=issuance_acceptance_unknown idempotencyKey={} reason=\"{}\" next=resend_same_key",
                    request.idempotencyKey(), result.reason());
        }
    }
}
