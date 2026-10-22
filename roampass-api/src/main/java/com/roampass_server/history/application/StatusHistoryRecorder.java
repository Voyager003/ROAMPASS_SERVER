package com.roampass_server.history.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 상태 변경 이력 기록 창구. 다른 도메인의 유스케이스가 상태를 바꾼 같은 트랜잭션 안에서 호출
 * <p>
 * 모든 상태 전이가 이곳을 지나므로 전이 로그({@code event=status_changed})도 여기서 한 번에 남김.
 * 로그는 트랜잭션이 커밋된 뒤에만 찍어, 롤백된 전이(조건부 UPDATE 경합에서 진 쪽 등)가 로그에 남지 않게 함
 * → 로그와 DB 이력이 항상 일치
 */
@Component
@RequiredArgsConstructor
public class StatusHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(StatusHistoryRecorder.class);

    private final StatusHistoryRepository repository;

    /**
     * @param from 이전 상태, 생성 기록이면 {@code null}
     */
    public void record(OrderNo orderNo, HistoryTarget target, Enum<?> from, Enum<?> to, Actor actor,
                       String reason, Instant occurredAt) {
        repository.append(StatusHistory.record(orderNo, target, from, to, actor, reason, occurredAt));
        logAfterCommit(orderNo, target, from, to, actor, reason);
    }

    private static void logAfterCommit(OrderNo orderNo, HistoryTarget target, Enum<?> from, Enum<?> to, Actor actor,
                                       String reason) {
        Runnable write = () -> log.info(
                "event=status_changed orderNo={} target={} targetKey={} from={} to={} actor={} reason=\"{}\"",
                orderNo.value(), target.type(), loggableKey(target), from == null ? "-" : from.name(), to.name(),
                actor.asString(), reason);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    /** SIM 프로필의 열쇠는 ICCID라 뒤 4자리만 남김 */
    private static String loggableKey(HistoryTarget target) {
        if (target.type() != TargetType.SIM_PROFILE) {
            return target.key();
        }
        String key = target.key();
        return key.length() <= 4 ? "****" : "****" + key.substring(key.length() - 4);
    }
}
