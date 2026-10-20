package com.roampass_server.issuance.domain.model;

import java.time.Duration;
import java.time.Instant;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 주문에 대한 공급사 발급 요청 1건(집합 루트). 불변
 * 우리 주문번호와 공급사 접수 번호를 잇고, 콜백 대기 시간의 기준(requestedAt)을 보관
 * 멱등 키 {@code {orderNo}-ISSUE-{attempt}}: 접수 응답을 못 받아 다시 보내도 공급사는 같은 접수 번호 반환
 */
public final class IssuanceRequest {

    private final Long id;
    private final OrderNo orderNo;
    private final int attempt;
    private final SupplierOrderNo supplierOrderNo;
    private final IssuanceStatus status;
    private final String failureReason;
    private final Instant requestedAt;
    private final Instant completedAt;

    private IssuanceRequest(Long id, OrderNo orderNo, int attempt, SupplierOrderNo supplierOrderNo,
                            IssuanceStatus status, String failureReason, Instant requestedAt, Instant completedAt) {
        this.id = id;
        this.orderNo = orderNo;
        this.attempt = attempt;
        this.supplierOrderNo = supplierOrderNo;
        this.status = status;
        this.failureReason = failureReason;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }

    public static IssuanceRequest start(OrderNo orderNo, int attempt, Instant now) {
        if (orderNo == null || now == null || attempt < 1) {
            throw new InvalidValueException("INVALID_ISSUANCE", "Order number, attempt (>= 1) and time are required");
        }
        return new IssuanceRequest(null, orderNo, attempt, null, IssuanceStatus.PENDING, null, now, null);
    }

    /** 저장소에서 읽은 값으로 복원, 필수값만 확인 */
    public static IssuanceRequest restore(Long id, OrderNo orderNo, int attempt, SupplierOrderNo supplierOrderNo,
                                          IssuanceStatus status, String failureReason, Instant requestedAt,
                                          Instant completedAt) {
        if (orderNo == null || status == null || requestedAt == null) {
            throw new InvalidValueException("INVALID_ISSUANCE", "Stored issuance request is missing required values");
        }
        return new IssuanceRequest(id, orderNo, attempt, supplierOrderNo, status, failureReason, requestedAt,
                completedAt);
    }

    public String idempotencyKey() {
        return orderNo.value() + "-ISSUE-" + attempt;
    }

    /** 공급사 접수. 같은 접수 번호가 다시 오면 변화 없음, 콜백이 먼저 와서 이미 끝난 요청도 변화 없음 */
    public IssuanceRequest accept(SupplierOrderNo number) {
        requireSameSupplierOrder(number);
        if (status != IssuanceStatus.PENDING) {
            return this;
        }
        return new IssuanceRequest(id, orderNo, attempt, number, IssuanceStatus.ACCEPTED, null, requestedAt, null);
    }

    /** 발급 완료 콜백 반영. 접수 응답보다 콜백이 먼저 와도(PENDING) 완료 가능 */
    public IssuanceRequest complete(SupplierOrderNo number, Instant now) {
        requireSameSupplierOrder(number);
        requireOpen(IssuanceStatus.COMPLETED);
        return new IssuanceRequest(id, orderNo, attempt, number, IssuanceStatus.COMPLETED, null, requestedAt, now);
    }

    public IssuanceRequest fail(String reason, Instant now) {
        requireOpen(IssuanceStatus.FAILED);
        return new IssuanceRequest(id, orderNo, attempt, supplierOrderNo, IssuanceStatus.FAILED, reason, requestedAt,
                now);
    }

    /** 요청 후 {@code timeout}이 지나도록 결과가 없는지 */
    public boolean isOverdue(Instant now, Duration timeout) {
        return status.isOpen() && !now.isBefore(requestedAt.plus(timeout));
    }

    private void requireSameSupplierOrder(SupplierOrderNo number) {
        if (number == null) {
            throw new InvalidValueException("INVALID_ISSUANCE", "Supplier order number is required");
        }
        if (supplierOrderNo != null && !supplierOrderNo.equals(number)) {
            throw new InvalidValueException("SUPPLIER_ORDER_MISMATCH",
                    "Supplier order number does not match the accepted one");
        }
    }

    private void requireOpen(IssuanceStatus target) {
        if (!status.isOpen()) {
            throw new IllegalStateTransitionException("IssuanceRequest", status, target);
        }
    }

    public Long id() {
        return id;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public int attempt() {
        return attempt;
    }

    public SupplierOrderNo supplierOrderNo() {
        return supplierOrderNo;
    }

    public IssuanceStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
