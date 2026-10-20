package com.roampass_server.issuance.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;

/**
 * 주문에 대해 내준 SIM(집합 루트, 주문당 1개). 불변, 식별은 주문번호
 * <ul>
 *   <li>설치 상태는 앞으로만 진행, 늦거나 중복된 신호는 {@code this}를 그대로 반환</li>
 *   <li>개통 시점에 사용 기간 확정(activatedAt + 일수 × 24시간)</li>
 *   <li>한 번도 설치·개통되지 않은 SIM만 환불 가능(QR 열람만으로는 설치가 아님)</li>
 *   <li>기기에서 삭제되면 {@code deletedAt}으로 기록, 상태는 도달한 가장 먼 단계 유지</li>
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

    /** 공급사 발급 콜백으로 만드는 이심 프로필 */
    public static SimProfile issueEsim(OrderNo orderNo, SupplierOrderNo supplierOrderNo, Iccid iccid,
                                       ActivationCode activationCode, int usageDays, Instant now) {
        if (supplierOrderNo == null || activationCode == null) {
            throw new InvalidValueException(CODE, "An eSIM needs a supplier order number and an activation code");
        }
        validateCommon(orderNo, iccid, usageDays, now);
        return new SimProfile(null, orderNo, SimType.ESIM, iccid, activationCode, supplierOrderNo, usageDays,
                InstallStatus.ISSUED, now, null, null, null, null);
    }

    /** 저장소에서 읽은 값으로 복원, 필수값만 확인 */
    public static SimProfile restore(Long id, OrderNo orderNo, SimType simType, Iccid iccid,
                                     ActivationCode activationCode, SupplierOrderNo supplierOrderNo, int usageDays,
                                     InstallStatus status, Instant issuedAt, Instant qrViewedAt, Instant installedAt,
                                     Instant activatedAt, Instant deletedAt) {
        if (orderNo == null || simType == null || iccid == null || status == null || issuedAt == null) {
            throw new InvalidValueException(CODE, "Stored SIM profile is missing required values");
        }
        return new SimProfile(id, orderNo, simType, iccid, activationCode, supplierOrderNo, usageDays, status,
                issuedAt, qrViewedAt, installedAt, activatedAt, deletedAt);
    }

    /** 공항에서 받은 카드에 인쇄된 ICCID를 사용자가 등록할 때 만드는 유심 프로필 */
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
            return this; // 이미 그 단계이거나 더 진행된 상태
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
     * @param at 공급사가 알려 준 개통 시각(UTC), 사용 기간의 시작점
     */
    public SimProfile markActivated(Instant at) {
        if (!InstallStatus.ACTIVE.isBeyond(status)) {
            return this;
        }
        // 설치 신호를 놓쳤더라도 개통은 설치를 포함
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
     * 기기에서 프로필이 지워진 사실을 기록. 상태는 도달한 가장 먼 단계 유지
     * 프로필은 내려받은 뒤에만 지울 수 있으므로 설치 시각이 비어 있으면 채움
     */
    public SimProfile markDeleted(Instant at) {
        if (deletedAt != null) {
            return this;
        }
        Instant installed = installedAt != null ? installedAt : at;
        return copy(status, qrViewedAt, installed, activatedAt, at);
    }

    /** {@code now} 시점에 보이는 상태: 만료 시각이 지난 ACTIVE SIM은 EXPIRED로 판정 */
    public InstallStatus effectiveStatus(Instant now) {
        if (status == InstallStatus.ACTIVE && usagePeriod().isExpired(now)) {
            return InstallStatus.EXPIRED;
        }
        return status;
    }

    /** 한 번도 설치·개통되지 않은 SIM만 환불 가능 */
    public boolean isRefundable() {
        return installedAt == null && activatedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 개통 후의 사용 기간, 개통 전에는 {@code null} */
    public UsagePeriod usagePeriod() {
        return activatedAt == null ? null : UsagePeriod.of(activatedAt, usageDays);
    }

    /** 사용 기간의 남은 시간, 개통 전에는 {@code null} */
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
