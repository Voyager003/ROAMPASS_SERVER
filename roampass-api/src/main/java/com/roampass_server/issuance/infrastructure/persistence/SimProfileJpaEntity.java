package com.roampass_server.issuance.infrastructure.persistence;

import java.time.Instant;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.ActivationCode;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
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
 * sim_profile 테이블 매핑
 */
@Entity
@Table(name = "sim_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SimProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "sim_type", nullable = false)
    private SimType simType;

    @Column(nullable = false, unique = true)
    private String iccid;

    @Column(name = "activation_code")
    private String activationCode;

    @Column(name = "supplier_order_no", unique = true)
    private String supplierOrderNo;

    @Column(name = "usage_days", nullable = false)
    private int usageDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "install_status", nullable = false)
    private InstallStatus installStatus;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "qr_viewed_at")
    private Instant qrViewedAt;

    @Column(name = "installed_at")
    private Instant installedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private SimProfileJpaEntity(SimProfile profile) {
        this.id = profile.id();
        this.orderNo = profile.orderNo().value();
        this.simType = profile.simType();
        this.iccid = profile.iccid().value();
        this.activationCode = profile.activationCode() == null ? null : profile.activationCode().value();
        this.supplierOrderNo = profile.supplierOrderNo() == null ? null : profile.supplierOrderNo().value();
        this.usageDays = profile.usageDays();
        this.installStatus = profile.status();
        this.issuedAt = profile.issuedAt();
        this.qrViewedAt = profile.qrViewedAt();
        this.installedAt = profile.installedAt();
        this.activatedAt = profile.activatedAt();
        this.deletedAt = profile.deletedAt();
    }

    static SimProfileJpaEntity fromDomain(SimProfile profile) {
        return new SimProfileJpaEntity(profile);
    }

    SimProfile toDomain() {
        return SimProfile.restore(id, OrderNo.of(orderNo), simType, Iccid.of(iccid),
                activationCode == null ? null : ActivationCode.of(activationCode),
                supplierOrderNo == null ? null : SupplierOrderNo.of(supplierOrderNo), usageDays, installStatus,
                issuedAt, qrViewedAt, installedAt, activatedAt, deletedAt);
    }
}
