package com.roampass_server.order.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roampass_server.order.application.OrderExpirationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 대기 만료 주기 실행. DB 상태 기준 폴링이라 재시작·다중 실행에도 조건부 UPDATE로 중복 전이 없음
 * 실패는 로그만 남기고 다음 주기에 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderExpirationService expirationService;

    @Scheduled(fixedDelayString = "${roampass.order.expiration.fixed-delay}",
            initialDelayString = "${roampass.order.expiration.fixed-delay}")
    public void expireDueOrders() {
        try {
            int expired = expirationService.expireDue();
            if (expired > 0) {
                log.info("Expired {} pending orders", expired);
            }
        } catch (RuntimeException e) {
            log.error("Order expiration run failed", e);
        }
    }
}
