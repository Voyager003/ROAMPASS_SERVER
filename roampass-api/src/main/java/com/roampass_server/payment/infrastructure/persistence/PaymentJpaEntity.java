package com.roampass_server.payment.infrastructure.persistence;

import java.time.Instant;

import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;

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
 * payment 테이블 매핑. 도메인 Payment와 1:1 변환만, 주문은 주문번호로만 참조
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "pg_tid")
    private String pgTid;

    @Column(name = "cancelled_amount", nullable = false)
    private long cancelledAmount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    private PaymentJpaEntity(Payment payment) {
        this.id = payment.id();
        this.idempotencyKey = payment.idempotencyKey().value();
        this.orderNo = payment.orderNo().value();
        this.amount = payment.amount().amount();
        this.currency = payment.amount().currency();
        this.status = payment.status();
        this.pgTid = payment.pgTid() == null ? null : payment.pgTid().value();
        this.cancelledAmount = payment.cancelledAmount().amount();
        this.failureReason = payment.failureReason();
        this.requestedAt = payment.requestedAt();
        this.approvedAt = payment.approvedAt();
    }

    static PaymentJpaEntity fromDomain(Payment payment) {
        return new PaymentJpaEntity(payment);
    }

    Payment toDomain() {
        return Payment.restore(id, OrderNo.of(orderNo), IdempotencyKey.from(idempotencyKey), Money.of(amount, currency),
                status, pgTid == null ? null : PgTid.of(pgTid), Money.of(cancelledAmount, currency), failureReason,
                requestedAt, approvedAt);
    }
}
