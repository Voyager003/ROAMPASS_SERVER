package com.roampass_server.payment.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roampass_server.payment.application.PaymentReconciliationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 응답 유실 결제 정리 주기 실행. 실패는 로그만 남기고 다음 주기에 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${roampass.pg.reconciliation.fixed-delay}",
            initialDelayString = "${roampass.pg.reconciliation.fixed-delay}")
    public void reconcile() {
        try {
            int settled = reconciliationService.reconcile();
            if (settled > 0) {
                log.info("event=scheduler_run job=payment_reconciliation processed={}", settled);
            }
        } catch (RuntimeException e) {
            log.error("event=scheduler_failed job=payment_reconciliation", e);
        }
    }
}
