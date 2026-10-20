package com.roampass_server.issuance.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;

/**
 * The SIM handed out for an order (aggregate root, one per order). Immutable; identity is the order number.
 * <ul>
 *   <li>Install status only moves forward; late or duplicate signals return {@code this} unchanged.</li>
 *   <li>Activation fixes the usage period (activatedAt + days × 24h).</li>
 *   <li>A SIM is refundable only if it was never installed or activated (QR viewing alone does not count).</li>
 *   <li>Deletion from the device is recorded as {@code deletedAt}; the status keeps the furthest stage reached.</li>
 * </ul>
 */
public final class SimProfile {

    private static final String CODE = "INVALID_SIM_PROFILE";

    private final Long id;
    private final OrderNo orderNo;
    private final SimType simType;
    private final Iccid iccid;
    private final ActivationCode activationCode;
    private final SupplierOrderNo supplierOrderNo;
    private final int usageDays;
    private final InstallStatus status;
    private final Instant issuedAt;
    private final Instant qrViewedAt;
    private final Instant installedAt;
    private final Instant activatedAt;
    private final Instant deletedAt;

    private SimProfile(Long id, OrderNo orderNo, SimType simType, Iccid iccid, ActivationCode activationCode,
                       SupplierOrderNo supplierOrderNo, int usageDays, InstallStatus status, Instant issuedAt,
                       Instant qrViewedAt, Instant installedAt, Instant activatedAt, Instant deletedAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.simType = simType;
        this.iccid = iccid;
        this.activationCode = activationCode;
        this.supplierOrderNo = supplierOrderNo;
        this.usageDays = usageDays;
        this.status = status;
        this.issuedAt = issuedAt;
        this.qrViewedAt = qrViewedAt;
        this.installedAt = installedAt;
        this.activatedAt = activatedAt;
        this.deletedAt = deletedAt;
    }

    /** eSIM profile created from the supplier's issuance callback. */
    public static SimProfile issueEsim(OrderNo orderNo, SupplierOrderNo supplierOrderNo, Iccid iccid,
                                       ActivationCode activationCode, int usageDays, Instant now) {
        if (supplierOrderNo == null || activationCode == null) {
            throw new InvalidValueException(CODE, "An eSIM needs a supplier order number and an activation code");
        }
        validateCommon(orderNo, iccid, usageDays, now);
        return new SimProfile(null, orderNo, SimType.ESIM, iccid, activationCode, supplierOrderNo, usageDays,
                InstallStatus.ISSUED, now, null, null, null, null);
    }

    /** USIM profile created when the user registers the ICCID printed on the card picked up at the airport. */
    public static SimProfile registerUsim(OrderNo orderNo, Iccid iccid, int usageDays, Instant now) {
        validateCommon(orderNo, iccid, usageDays, now);
        return new SimProfile(null, orderNo, SimType.USIM, iccid, null, null, usageDays,
                InstallStatus.ISSUED, now, null, null, null, null);
    }

    private static void validateCommon(OrderNo orderNo, Iccid iccid, int usageDays, Instant now) {
        if (orderNo == null || iccid == null || now == null) {
            throw new InvalidValueException(CODE, "Order number, ICCID and time are required");
        }
        if (usageDays < 1) {
            throw new InvalidValueException(CODE, "Usage days must be at least 1");
        }
    }

    public SimProfile markQrViewed(Instant at) {
        if (simType != SimType.ESIM) {
            throw new IllegalStateTransitionException("SimProfile", status, InstallStatus.QR_VIEWED + " (USIM has no QR)");
        }
        if (!InstallStatus.QR_VIEWED.isBeyond(status)) {
            return this; // already there or further along
        }
        return copy(InstallStatus.QR_VIEWED, at, installedAt, activatedAt, deletedAt);
    }

    public SimProfile markInstalled(Instant at) {
        if (!InstallStatus.INSTALLED.isBeyond(status)) {
            return this;
        }
        return copy(InstallStatus.INSTALLED, qrViewedAt, at, activatedAt, deletedAt);
    }

    /**
     * @param at activation time reported by the supplier (UTC); the usage period starts here
     */
    public SimProfile markActivated(Instant at) {
        if (!InstallStatus.ACTIVE.isBeyond(status)) {
            return this;
        }
        // Activation implies installation even if the install signal was missed.
        Instant installed = installedAt != null ? installedAt : at;
        return copy(InstallStatus.ACTIVE, qrViewedAt, installed, at, deletedAt);
    }

    public SimProfile markExpired(Instant now) {
        if (status != InstallStatus.ACTIVE || !usagePeriod().isExpired(now)) {
            throw new IllegalStateTransitionException("SimProfile", status, InstallStatus.EXPIRED + " (only an active SIM past its expiry)");
        }
        return copy(InstallStatus.EXPIRED, qrViewedAt, installedAt, activatedAt, deletedAt);
    }

    /**
     * Records that the profile was removed from the device. The status keeps the furthest stage reached;
     * since a profile can only be deleted after it was downloaded, a missing install time is filled in.
     */
    public SimProfile markDeleted(Instant at) {
        if (deletedAt != null) {
            return this;
        }
        Instant installed = installedAt != null ? installedAt : at;
        return copy(status, qrViewedAt, installed, activatedAt, at);
    }

    /** Status as seen at {@code now}: an ACTIVE SIM past its expiry is reported as EXPIRED. */
    public InstallStatus effectiveStatus(Instant now) {
        if (status == InstallStatus.ACTIVE && usagePeriod().isExpired(now)) {
            return InstallStatus.EXPIRED;
        }
        return status;
    }

    /** Refundable only if the SIM was never installed or activated. */
    public boolean isRefundable() {
        return installedAt == null && activatedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Usage period once activated; {@code null} before activation. */
    public UsagePeriod usagePeriod() {
        return activatedAt == null ? null : UsagePeriod.of(activatedAt, usageDays);
    }

    /** Time left in the usage period; {@code null} before activation. */
    public Duration remaining(Instant now) {
        UsagePeriod period = usagePeriod();
        return period == null ? null : period.remaining(now);
    }

    private SimProfile copy(InstallStatus newStatus, Instant qrViewed, Instant installed, Instant activated, Instant deleted) {
        return new SimProfile(id, orderNo, simType, iccid, activationCode, supplierOrderNo, usageDays, newStatus,
                issuedAt, qrViewed, installed, activated, deleted);
    }

    public Long id() {
        return id;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public SimType simType() {
        return simType;
    }

    public Iccid iccid() {
        return iccid;
    }

    public ActivationCode activationCode() {
        return activationCode;
    }

    public SupplierOrderNo supplierOrderNo() {
        return supplierOrderNo;
    }

    public int usageDays() {
        return usageDays;
    }

    public InstallStatus status() {
        return status;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant qrViewedAt() {
        return qrViewedAt;
    }

    public Instant installedAt() {
        return installedAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof SimProfile other && orderNo.equals(other.orderNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNo);
    }
}
