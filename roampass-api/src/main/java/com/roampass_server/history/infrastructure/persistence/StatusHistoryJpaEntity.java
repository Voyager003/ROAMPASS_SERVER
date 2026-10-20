package com.roampass_server.history.infrastructure.persistence;

import java.time.Instant;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * status_history 테이블 매핑. 추가 전용(수정 경로 없음)
 */
@Entity
@Table(name = "status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_key", nullable = false)
    private String targetKey;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(nullable = false)
    private String actor;

    @Column
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    private StatusHistoryJpaEntity(StatusHistory history) {
        this.orderNo = history.orderNo().value();
        this.targetType = history.target().type();
        this.targetKey = history.target().key();
        this.fromStatus = history.fromStatus();
        this.toStatus = history.toStatus();
        this.actor = history.actor().asString();
        this.reason = history.reason();
        this.occurredAt = history.occurredAt();
    }

    static StatusHistoryJpaEntity fromDomain(StatusHistory history) {
        return new StatusHistoryJpaEntity(history);
    }

    StatusHistory toDomain() {
        return StatusHistory.restore(id, OrderNo.of(orderNo), HistoryTarget.of(targetType, targetKey), fromStatus,
                toStatus, Actor.parse(actor), reason, occurredAt);
    }
}
