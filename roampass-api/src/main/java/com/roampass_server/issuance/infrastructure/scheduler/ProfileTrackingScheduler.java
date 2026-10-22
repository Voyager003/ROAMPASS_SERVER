package com.roampass_server.issuance.infrastructure.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.roampass_server.issuance.application.ProfileTrackingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 설치·개통 추적 폴링 주기 작업. 실패는 로그만 남기고 다음 주기에 재시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileTrackingScheduler {

    private final ProfileTrackingService trackingService;

    @Scheduled(fixedDelayString = "${roampass.tracking.fixed-delay}",
            initialDelayString = "${roampass.tracking.fixed-delay}")
    public void poll() {
        try {
            int changed = trackingService.poll();
            if (changed > 0) {
                log.info("event=scheduler_run job=profile_tracking processed={}", changed);
            }
        } catch (RuntimeException e) {
            log.error("event=scheduler_failed job=profile_tracking", e);
        }
    }
}
