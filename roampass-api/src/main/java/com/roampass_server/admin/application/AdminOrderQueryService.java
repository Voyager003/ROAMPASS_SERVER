package com.roampass_server.admin.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.admin.application.AdminOrderDetail.CallbackInfo;
import com.roampass_server.admin.application.AdminOrderDetail.IssuanceInfo;
import com.roampass_server.admin.application.AdminOrderDetail.OrderInfo;
import com.roampass_server.admin.application.AdminOrderDetail.PaymentInfo;
import com.roampass_server.admin.application.AdminOrderDetail.ProfileInfo;
import com.roampass_server.admin.application.AdminOrderDetail.RefundInfo;
import com.roampass_server.admin.application.AdminOrderDetail.TimelineEntry;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

/**
 * 어드민 주문 조회. 목록은 검색 포트로 주문번호를 고른 뒤 행마다 주문·최근 결제·프로필을 추가 조회(N+1, 페이지 크기 최대 50으로 제한)
 * 주문·결제 상태는 저장된 값 그대로(운영자는 시스템이 실제로 기록한 상태를 봄)
 * 설치 상태만 조회 시점 기준: 만료는 저장하는 단계가 없어(시간으로 판정) 저장값 그대로면 EXPIRED가 보이지 않음
 */
@Transactional(readOnly = true)
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SimProfileRepository profileRepository;
    private final IssuanceRequestRepository issuanceRequestRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final StatusHistoryRepository historyRepository;
    private final CallbackLogRepository callbackLogRepository;
    private final AdminOrderSearch orderSearch;
    private final Clock clock;

    public AdminOrderQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                  SimProfileRepository profileRepository,
                                  IssuanceRequestRepository issuanceRequestRepository,
                                  RefundAttemptRepository refundAttemptRepository,
                                  StatusHistoryRepository historyRepository,
                                  CallbackLogRepository callbackLogRepository, AdminOrderSearch orderSearch,
                                  Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.profileRepository = profileRepository;
        this.issuanceRequestRepository = issuanceRequestRepository;
        this.refundAttemptRepository = refundAttemptRepository;
        this.historyRepository = historyRepository;
        this.callbackLogRepository = callbackLogRepository;
        this.orderSearch = orderSearch;
        this.clock = clock;
    }

    public PageResult<AdminOrderSummary> search(AdminOrderSearchCondition condition, int page, int size) {
        return orderSearch.search(condition, Instant.now(clock), page, size)
                .map(orderNo -> summarize(orderRepository.findByOrderNo(orderNo)
                        .orElseThrow(() -> new IllegalStateException("Searched order disappeared: " + orderNo.value()))));
    }

    public AdminOrderDetail detail(OrderNo orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        return new AdminOrderDetail(
                new OrderInfo(order.orderNo().value(), order.userId().value(), order.planId(), order.simType(),
                        order.amount(), order.travelStartDate(), order.status(), order.createdAt(), order.expiresAt(),
                        order.issuedAt()),
                paymentRepository.findByOrderNo(orderNo).stream()
                        .map(p -> new PaymentInfo(p.idempotencyKey().value(), p.status(), p.amount(),
                                p.cancelledAmount(), p.pgTid() == null ? null : p.pgTid().value(), p.failureReason(),
                                p.requestedAt(), p.approvedAt()))
                        .toList(),
                issuanceRequestRepository.findByOrderNo(orderNo).stream()
                        .map(r -> new IssuanceInfo(r.attempt(), r.status(),
                                r.supplierOrderNo() == null ? null : r.supplierOrderNo().value(), r.failureReason(),
                                r.requestedAt(), r.completedAt()))
                        .toList(),
                profileRepository.findByOrderNo(orderNo).map(p -> profileInfo(p, Instant.now(clock))).orElse(null),
                refundAttemptRepository.findByOrderNo(orderNo).stream()
                        .map(r -> new RefundInfo(r.id(), r.status(), r.amount(), r.attemptCount(), r.nextRetryAt(),
                                r.lastError(), r.createdAt(), r.completedAt()))
                        .toList(),
                historyRepository.findByOrderNo(orderNo).stream()
                        .map(h -> new TimelineEntry(h.occurredAt(), h.target().type().name(), h.target().key(),
                                h.fromStatus(), h.toStatus(), h.actor().asString(), h.reason()))
                        .toList(),
                callbackLogRepository.findByOrderNo(orderNo).stream()
                        .map(c -> new CallbackInfo(c.source(), c.eventKey(), c.signatureOk(), c.outcome(), c.note(),
                                c.receivedAt()))
                        .toList());
    }

    /** 목록 한 줄(이상 건 목록도 같은 형식) */
    public AdminOrderSummary summarize(Order order) {
        List<Payment> payments = paymentRepository.findByOrderNo(order.orderNo());
        Payment latest = payments.stream().max(Comparator.comparing(Payment::requestedAt)).orElse(null);
        SimProfile profile = profileRepository.findByOrderNo(order.orderNo()).orElse(null);
        return new AdminOrderSummary(order.orderNo().value(), order.userId().value(), order.simType(), order.amount(),
                order.status(), latest == null ? null : latest.status(),
                profile == null ? null : profile.effectiveStatus(Instant.now(clock)),
                order.createdAt());
    }

    private static ProfileInfo profileInfo(SimProfile p, Instant now) {
        boolean activated = p.activatedAt() != null;
        return new ProfileInfo(p.simType(), p.iccid().value(), p.effectiveStatus(now), p.usageDays(), p.issuedAt(),
                p.qrViewedAt(), p.installedAt(), p.activatedAt(), p.deletedAt(),
                activated ? p.usagePeriod().expiresAt() : null, activated ? p.usagePeriod().expiresAtKst() : null,
                p.isRefundable());
    }
}
