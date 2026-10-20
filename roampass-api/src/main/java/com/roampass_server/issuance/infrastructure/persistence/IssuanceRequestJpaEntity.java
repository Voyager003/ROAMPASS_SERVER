package com.roampass_server.issuance.infrastructure.persistence;

import java.time.Instant;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;

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
 * issuance_request 테이블 매핑
 */
@Entity
@Table(name = "issuance_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuanceRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "supplier_order_no", unique = true)
    private String supplierOrderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuanceStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private IssuanceRequestJpaEntity(IssuanceRequest request) {
        this.id = request.id();
        this.orderNo = request.orderNo().value();
        this.attempt = request.attempt();
        this.supplierOrderNo = request.supplierOrderNo() == null ? null : request.supplierOrderNo().value();
        this.status = request.status();
        this.failureReason = request.failureReason();
        this.requestedAt = request.requestedAt();
        this.completedAt = request.completedAt();
    }

    static IssuanceRequestJpaEntity fromDomain(IssuanceRequest request) {
        return new IssuanceRequestJpaEntity(request);
    }

    IssuanceRequest toDomain() {
        return IssuanceRequest.restore(id, OrderNo.of(orderNo), attempt,
                supplierOrderNo == null ? null : SupplierOrderNo.of(supplierOrderNo), status, failureReason,
                requestedAt, completedAt);
    }
}
