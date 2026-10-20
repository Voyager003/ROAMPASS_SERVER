package com.roampass_server.history.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상태 변경 이력 기록 창구. 다른 도메인의 유스케이스가 상태를 바꾼 같은 트랜잭션 안에서 호출
 */
@Component
@RequiredArgsConstructor
public class StatusHistoryRecorder {

    private final StatusHistoryRepository repository;

    /**
     * @param from 이전 상태, 생성 기록이면 {@code null}
     */
    public void record(OrderNo orderNo, HistoryTarget target, Enum<?> from, Enum<?> to, Actor actor,
                       String reason, Instant occurredAt) {
        repository.append(StatusHistory.record(orderNo, target, from, to, actor, reason, occurredAt));
    }
}
