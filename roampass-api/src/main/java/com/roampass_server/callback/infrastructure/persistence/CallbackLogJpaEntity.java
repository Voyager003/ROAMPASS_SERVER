package com.roampass_server.callback.infrastructure.persistence;

import java.time.Instant;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.common.domain.OrderNo;

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
 * callback_log 테이블 매핑
 */
@Entity
@Table(name = "callback_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallbackLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallbackSource source;

    @Column(name = "event_key", unique = true)
    private String eventKey;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "signature_ok", nullable = false)
    private boolean signatureOk;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallbackOutcome outcome;

    private String note;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    private CallbackLogJpaEntity(CallbackLog log) {
        this.id = log.id();
        this.source = log.source();
        this.eventKey = log.eventKey();
        this.orderNo = log.orderNo() == null ? null : log.orderNo().value();
        this.signatureOk = log.signatureOk();
        this.payload = log.payload();
        this.outcome = log.outcome();
        this.note = log.note();
        this.receivedAt = log.receivedAt();
    }

    static CallbackLogJpaEntity fromDomain(CallbackLog log) {
        return new CallbackLogJpaEntity(log);
    }

    CallbackLog toDomain() {
        return CallbackLog.restore(id, source, eventKey, orderNo == null ? null : OrderNo.of(orderNo), signatureOk,
                payload, outcome, note, receivedAt);
    }
}
