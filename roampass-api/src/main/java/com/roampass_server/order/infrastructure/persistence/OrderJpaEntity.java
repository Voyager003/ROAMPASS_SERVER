package com.roampass_server.order.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderIdempotencyKey;
import com.roampass_server.order.domain.model.OrderStatus;

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
 * orders 테이블 매핑. 비즈니스 로직 없음(도메인 Order가 담당), 도메인과 1:1 변환만
 * 요금제는 객체 연관관계 없이 id로만 참조
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sim_type", nullable = false)
    private SimType simType;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(name = "travel_start_date", nullable = false)
    private LocalDate travelStartDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    private OrderJpaEntity(Order order) {
        this.id = order.id();
        this.orderNo = order.orderNo().value();
        this.userId = order.userId().value();
        this.planId = order.planId();
        this.simType = order.simType();
        this.amount = order.amount().amount();
        this.currency = order.amount().currency();
        this.travelStartDate = order.travelStartDate();
        this.status = order.status();
        this.createdAt = order.createdAt();
        this.expiresAt = order.expiresAt();
        this.issuedAt = order.issuedAt();
        this.idempotencyKey = order.idempotencyKey() == null ? null : order.idempotencyKey().value();
    }

    static OrderJpaEntity fromDomain(Order order) {
        return new OrderJpaEntity(order);
    }

    Order toDomain() {
        return Order.restore(id, OrderNo.of(orderNo), UserId.of(userId), planId, simType, Money.of(amount, currency),
                travelStartDate, status, createdAt, expiresAt, issuedAt,
                idempotencyKey == null ? null : OrderIdempotencyKey.of(idempotencyKey));
    }
}
