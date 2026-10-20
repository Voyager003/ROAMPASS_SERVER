package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;

/**
 * 콜백 대기 시간을 넘긴 발급 요청을 실패 처리(→ 자동 환불). 스케줄러가 주기적으로 호출
 * 콜백 누락·공급사 장애로 ISSUE_REQUESTED에 멈춘 주문이 남지 않게 함
 */
public class IssuanceTimeoutService {

    private final IssuanceRequestRepository requestRepository;
    private final IssuanceFailureService failureService;
    private final Clock clock;
    private final Duration callbackTimeout;
    private final int batchSize;

    public IssuanceTimeoutService(IssuanceRequestRepository requestRepository, IssuanceFailureService failureService,
                                  Clock clock, Duration callbackTimeout, int batchSize) {
        this.requestRepository = requestRepository;
        this.failureService = failureService;
        this.clock = clock;
        this.callbackTimeout = callbackTimeout;
        this.batchSize = batchSize;
    }

    /** @return 실패 처리한 건수 */
    public int failOverdue() {
        Instant threshold = Instant.now(clock).minus(callbackTimeout);
        int failed = 0;
        for (IssuanceRequest request : requestRepository.findOpenRequestedBefore(threshold, batchSize)) {
            if (failureService.fail(request.orderNo(), "발급 콜백 대기 시간 초과", Actor.system())) {
                failed++;
            }
        }
        return failed;
    }
}
