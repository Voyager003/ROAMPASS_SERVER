package com.roampass_server.issuance.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roampass_server.issuance.application.IssuanceDispatchService;
import com.roampass_server.issuance.application.IssuanceTimeoutService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 발급 주기 작업 2개: 발급 요청(PAID 이심 → 공급사), 콜백 대기 초과 검사(→ 실패·자동 환불)
 * 실패는 로그만 남기고 다음 주기에 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IssuanceSchedulers {

    private final IssuanceDispatchService dispatchService;
    private final IssuanceTimeoutService timeoutService;

    @Scheduled(fixedDelayString = "${roampass.issuance.dispatch-fixed-delay}",
            initialDelayString = "${roampass.issuance.dispatch-fixed-delay}")
    public void dispatch() {
        try {
            int started = dispatchService.dispatch();
            if (started > 0) {
                log.info("Started issuance for {} orders", started);
            }
        } catch (RuntimeException e) {
            log.error("Issuance dispatch run failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${roampass.issuance.timeout-fixed-delay}",
            initialDelayString = "${roampass.issuance.timeout-fixed-delay}")
    public void failOverdue() {
        try {
            int failed = timeoutService.failOverdue();
            if (failed > 0) {
                log.warn("Failed {} issuances after callback timeout", failed);
            }
        } catch (RuntimeException e) {
            log.error("Issuance timeout run failed", e);
        }
    }
}
