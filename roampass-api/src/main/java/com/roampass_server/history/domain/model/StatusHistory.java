package com.roampass_server.history.domain.model;

import java.time.Instant;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

/**
 * 상태 변경 한 건: 언제, 무엇이(대상, 이전 → 이후), 누가 또는 어떤 신호로(주체), 왜(사유)
 * 추가 전용, 수정 메서드 없음. 모든 기록에 주문번호를 두어 어드민 타임라인에서
 * 주문 하나의 주문·결제·환불·SIM 변경을 한 목록으로 조회
 * 주문·결제·설치처럼 서로 다른 상태 흐름이 함께 쓰므로 상태는 enum 이름으로 저장
 * 운영자 조치는 사유 필수(과제 2.3-⑤)
 */
public final class StatusHistory {

    private static final String CODE = "INVALID_HISTORY";
    private static final int MAX_REASON_LENGTH = 500;

    private final Long id;
    private final OrderNo orderNo;
    private final HistoryTarget target;
    private final String fromStatus;
    private final String toStatus;
    private final Actor actor;
    private final String reason;
    private final Instant occurredAt;

    private StatusHistory(Long id, OrderNo orderNo, HistoryTarget target, String fromStatus, String toStatus,
                          Actor actor, String reason, Instant occurredAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.target = target;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
        this.occurredAt = occurredAt;
    }

    /**
     * @param from 이전 상태. 대상이 막 생성된 경우 {@code null}
     */
    public static StatusHistory record(OrderNo orderNo, HistoryTarget target, Enum<?> from, Enum<?> to,
                                       Actor actor, String reason, Instant occurredAt) {
        if (orderNo == null || target == null || to == null || actor == null || occurredAt == null) {
            throw new InvalidValueException(CODE, "Order number, target, new status, actor and time are required");
        }
        if (from != null && from.name().equals(to.name())) {
            throw new InvalidValueException(CODE, "A history entry must record an actual status change");
        }
        if (actor.isAdmin() && (reason == null || reason.isBlank())) {
            throw new InvalidValueException("ADMIN_REASON_REQUIRED", "Operator actions must state a reason");
        }
        return new StatusHistory(null, orderNo, target, from == null ? null : from.name(), to.name(), actor,
                truncate(reason), occurredAt);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    public Long id() {
        return id;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public HistoryTarget target() {
        return target;
    }

    public String fromStatus() {
        return fromStatus;
    }

    public String toStatus() {
        return toStatus;
    }

    public Actor actor() {
        return actor;
    }

    public String reason() {
        return reason;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
