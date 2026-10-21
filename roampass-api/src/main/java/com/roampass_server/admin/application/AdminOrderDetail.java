package com.roampass_server.admin.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.RefundStatus;

/**
 * 어드민 주문 상세: 주문 + 결제 시도·발급 요청·프로필·환불 시도·콜백 기록 + 상태 변경 타임라인
 */
public record AdminOrderDetail(OrderInfo order, List<PaymentInfo> payments, List<IssuanceInfo> issuanceRequests,
                               ProfileInfo profile, List<RefundInfo> refunds, List<TimelineEntry> timeline,
                               List<CallbackInfo> callbacks) {

    public record OrderInfo(String orderNo, String userId, Long planId, SimType simType, Money amount,
                            LocalDate travelStartDate, OrderStatus status, Instant createdAt, Instant expiresAt,
                            Instant issuedAt) {
    }

    public record PaymentInfo(String idempotencyKey, PaymentStatus status, Money amount, Money cancelledAmount,
                              String pgTid, String failureReason, Instant requestedAt, Instant approvedAt) {
    }

    public record IssuanceInfo(int attempt, IssuanceStatus status, String supplierOrderNo, String failureReason,
                               Instant requestedAt, Instant completedAt) {
    }

    /** expiresAt·expiresAtKst는 개통 전이면 null */
    public record ProfileInfo(SimType simType, String iccid, InstallStatus status, int usageDays, Instant issuedAt,
                              Instant qrViewedAt, Instant installedAt, Instant activatedAt, Instant deletedAt,
                              Instant expiresAt, LocalDateTime expiresAtKst, boolean refundable) {
    }

    public record RefundInfo(Long id, RefundStatus status, Money amount, int attemptCount, Instant nextRetryAt,
                             String lastError, Instant createdAt, Instant completedAt) {
    }

    /** 언제·무엇이(대상·전이)·누가(주체)·왜(사유) */
    public record TimelineEntry(Instant occurredAt, String targetType, String targetKey, String fromStatus,
                                String toStatus, String actor, String reason) {
    }

    public record CallbackInfo(CallbackSource source, String eventKey, boolean signatureOk, CallbackOutcome outcome,
                               String note, Instant receivedAt) {
    }
}
