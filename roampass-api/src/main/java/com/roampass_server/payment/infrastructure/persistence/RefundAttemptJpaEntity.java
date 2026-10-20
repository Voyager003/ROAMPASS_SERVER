package com.roampass_server.payment.infrastructure.persistence;

import java.time.Instant;

import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;

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
 * refund_attempt 테이블 매핑. 결제는 멱등 키로만 참조
 */
@Entity
@Table(name = "refund_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAttemptJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_key", nullable = false)
    private String paymentKey;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private RefundAttemptJpaEntity(RefundAttempt attempt) {
        this.id = attempt.id();
        this.paymentKey = attempt.paymentKey().value();
        this.orderNo = attempt.orderNo().value();
        this.amount = attempt.amount().amount();
        this.currency = attempt.amount().currency();
        this.status = attempt.status();
        this.attemptCount = attempt.attemptCount();
        this.nextRetryAt = attempt.nextRetryAt();
        this.lastError = attempt.lastError();
        this.createdAt = attempt.createdAt();
        this.completedAt = attempt.completedAt();
    }

    static RefundAttemptJpaEntity fromDomain(RefundAttempt attempt) {
        return new RefundAttemptJpaEntity(attempt);
    }

    RefundAttempt toDomain() {
        return RefundAttempt.restore(id, IdempotencyKey.from(paymentKey), OrderNo.of(orderNo),
                Money.of(amount, currency), status, attemptCount, nextRetryAt, lastError, createdAt, completedAt);
    }
}
