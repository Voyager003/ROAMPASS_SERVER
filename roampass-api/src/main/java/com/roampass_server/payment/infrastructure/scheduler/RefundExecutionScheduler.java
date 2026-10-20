package com.roampass_server.payment.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roampass_server.payment.application.RefundExecutionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 환불 실행 주기 작업(시도 시각이 된 환불 요청 → PG 취소). 실패는 로그만 남기고 다음 주기에 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundExecutionScheduler {

    private final RefundExecutionService refundExecutionService;

    @Scheduled(fixedDelayString = "${roampass.pg.refund.fixed-delay}",
            initialDelayString = "${roampass.pg.refund.fixed-delay}")
    public void processDue() {
        try {
            int refunded = refundExecutionService.processDue();
            if (refunded > 0) {
                log.info("Refunded {} orders", refunded);
            }
        } catch (RuntimeException e) {
            log.error("Refund execution run failed", e);
        }
    }
}
