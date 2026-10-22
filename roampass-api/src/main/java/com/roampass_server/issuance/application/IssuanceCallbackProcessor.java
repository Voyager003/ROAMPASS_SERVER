package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.logging.LogValues;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.ActivationCode;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.plan.application.PlanQueryService;

import lombok.extern.slf4j.Slf4j;

/**
 * 서명 검증을 통과한 공급사 콜백 처리(한 트랜잭션)
 * 중복 방어: 수신 기록 선 INSERT(같은 이벤트) → 발급 요청 조건부 UPDATE(다른 이벤트로 같은 결과)
 * → sim_profile 주문번호·ICCID 유니크(최후 방어). 결과적으로 한 주문에 프로필 1개
 */
@Slf4j
public class IssuanceCallbackProcessor {

    static final String ISSUED = "ISSUED";
    static final String ISSUE_FAILED = "ISSUE_FAILED";
    static final String ACTIVATED = "ACTIVATED";
    private static final int ATTEMPTS = 3;

    private final CallbackLogRepository callbackLogRepository;
    private final IssuanceRequestRepository requestRepository;
    private final SimProfileRepository profileRepository;
    private final OrderRepository orderRepository;
    private final PlanQueryService planQueryService;
    private final IssuanceFailureService failureService;
    private final StatusHistoryRecorder historyRecorder;
    private final ProfileSignalApplier profileSignalApplier;
    private final Clock clock;

    public IssuanceCallbackProcessor(CallbackLogRepository callbackLogRepository,
                                     IssuanceRequestRepository requestRepository,
                                     SimProfileRepository profileRepository, OrderRepository orderRepository,
                                     PlanQueryService planQueryService, IssuanceFailureService failureService,
                                     StatusHistoryRecorder historyRecorder,
                                     ProfileSignalApplier profileSignalApplier, Clock clock) {
        this.callbackLogRepository = callbackLogRepository;
        this.requestRepository = requestRepository;
        this.profileRepository = profileRepository;
        this.orderRepository = orderRepository;
        this.planQueryService = planQueryService;
        this.failureService = failureService;
        this.historyRecorder = historyRecorder;
        this.profileSignalApplier = profileSignalApplier;
        this.clock = clock;
    }

    public static String eventKey(String eventId) {
        return "SUPPLIER:" + eventId;
    }

    @Transactional
    public CallbackResult process(IssuanceCallbackCommand command, String rawBody) {
        if (command.eventId() == null || command.eventId().isBlank()) {
            throw new InvalidValueException("INVALID_CALLBACK", "eventId is required");
        }
        Instant now = Instant.now(clock);
        String eventKey = eventKey(command.eventId());
        if (ACTIVATED.equals(command.eventType())) {
            return processActivation(command, rawBody, eventKey, now);
        }
        OrderNo orderNo = OrderNo.of(command.orderNo());
        if (!callbackLogRepository.insertIfAbsent(
                CallbackLog.received(CallbackSource.SUPPLIER, eventKey, orderNo, rawBody, now))) {
            log.info("event=supplier_callback_duplicate eventId={}", eventKey);
            return CallbackResult.DUPLICATE;
        }
        Outcome outcome = requestRepository.findLatestByOrderNo(orderNo).isEmpty()
                ? new Outcome(CallbackResult.ORDER_NOT_FOUND, "UNKNOWN_ORDER")
                : switch (command.eventType() == null ? "" : command.eventType()) {
                    case ISSUED -> applyIssued(orderNo, command, now);
                    case ISSUE_FAILED -> failureService.fail(orderNo, "공급사 발급 실패: " + command.reason(),
                            Actor.supplier())
                            ? new Outcome(CallbackResult.PROCESSED, "ISSUE_FAILED")
                            : new Outcome(CallbackResult.ALREADY_APPLIED, "REQUEST_ALREADY_CLOSED");
                    default -> new Outcome(CallbackResult.IGNORED, "UNKNOWN_EVENT_TYPE");
                };
        callbackLogRepository.updateOutcome(eventKey, toLogOutcome(outcome.result()), outcome.note());
        return outcome.result();
    }

    /**
     * 개통 이벤트 콜백(과제 2.3-④). 대상은 ICCID로 찾음(유심 개통 콜백은 우리 주문번호가 없음)
     * 반영은 상태 조회(폴링)와 같은 코드: 설치 상태는 앞으로만 진행하므로 폴링이 먼저 반영했거나
     * 늦게 도착한 콜백은 변화 없이 ALREADY_APPLIED. 콜백이 빠지면 폴링이 메움
     */
    private CallbackResult processActivation(IssuanceCallbackCommand command, String rawBody, String eventKey,
                                             Instant now) {
        Iccid iccid = Iccid.of(command.iccid());
        Instant activatedAt = parseInstant(command.activatedAt());
        SimProfile profile = profileRepository.findByIccid(iccid).orElse(null);
        if (!callbackLogRepository.insertIfAbsent(CallbackLog.received(CallbackSource.SUPPLIER, eventKey,
                profile == null ? null : profile.orderNo(), rawBody, now))) {
            log.info("event=supplier_callback_duplicate eventId={}", eventKey);
            return CallbackResult.DUPLICATE;
        }
        Outcome outcome;
        if (profile == null) {
            log.warn("event=supplier_callback_ignored reason=unknown_iccid iccid={}", LogValues.iccid(iccid.value()));
            outcome = new Outcome(CallbackResult.ORDER_NOT_FOUND, "UNKNOWN_ICCID");
        } else if (profileSignalApplier.apply(profile,
                SupplierProfileResult.found(SupplierProfileState.ENABLED, activatedAt, 0),
                ProfileSignalApplier.SOURCE_CALLBACK)) {
            outcome = new Outcome(CallbackResult.PROCESSED, "ACTIVATED");
        } else {
            outcome = new Outcome(CallbackResult.ALREADY_APPLIED, "ALREADY_ACTIVE");
        }
        callbackLogRepository.updateOutcome(eventKey, toLogOutcome(outcome.result()), outcome.note());
        return outcome.result();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null; // 반영 시점 시각으로 대체(ProfileSignalApplier)
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidValueException("INVALID_CALLBACK", "activatedAt must be an ISO-8601 instant");
        }
    }

    // 접수 응답(PENDING → ACCEPTED)과 경합할 수 있어 다시 읽고 재시도
    private Outcome applyIssued(OrderNo orderNo, IssuanceCallbackCommand command, Instant now) {
        SupplierOrderNo supplierOrderNo = SupplierOrderNo.of(command.supplierOrderNo());
        for (int i = 0; i < ATTEMPTS; i++) {
            IssuanceRequest request = requestRepository.findLatestByOrderNo(orderNo).orElseThrow();
            if (request.status() == IssuanceStatus.COMPLETED) {
                return new Outcome(CallbackResult.ALREADY_APPLIED, "ALREADY_ISSUED");
            }
            if (request.status() == IssuanceStatus.FAILED) {
                log.error("event=late_issuance_after_failure orderNo={} supplierOrderNo={} action=profile_not_attached",
                        orderNo.value(), supplierOrderNo.value());
                return new Outcome(CallbackResult.IGNORED, "LATE_ISSUANCE");
            }
            IssuanceRequest completed;
            try {
                completed = request.complete(supplierOrderNo, now);
            } catch (InvalidValueException e) {
                log.error("event=supplier_order_mismatch orderNo={} detail=\"{}\"", orderNo.value(), e.getMessage());
                return new Outcome(CallbackResult.IGNORED, "SUPPLIER_ORDER_MISMATCH");
            }
            if (requestRepository.saveIfStatus(completed, request.status())) {
                attachProfile(orderNo, supplierOrderNo, command, now);
                return new Outcome(CallbackResult.PROCESSED, "ISSUED");
            }
        }
        throw new IllegalStateException("Issuance request kept changing: " + orderNo.value());
    }

    private void attachProfile(OrderNo orderNo, SupplierOrderNo supplierOrderNo, IssuanceCallbackCommand command,
                               Instant now) {
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        int usageDays = planQueryService.getPlan(order.planId()).spec().days();
        Iccid iccid = Iccid.of(command.iccid());
        profileRepository.save(SimProfile.issueEsim(orderNo, supplierOrderNo, iccid,
                ActivationCode.of(command.activationCode()), usageDays, now));
        if (!orderRepository.saveIfStatus(order.markIssued(now), OrderStatus.ISSUE_REQUESTED)) {
            throw new IllegalStateException("Order is not ISSUE_REQUESTED: " + orderNo.value());
        }
        historyRecorder.record(orderNo, HistoryTarget.of(TargetType.SIM_PROFILE, iccid.value()), null,
                InstallStatus.ISSUED, Actor.supplier(), "프로필 발급", now);
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), OrderStatus.ISSUE_REQUESTED, OrderStatus.ISSUED,
                Actor.supplier(), "발급 완료", now);
    }

    private static CallbackOutcome toLogOutcome(CallbackResult result) {
        return switch (result) {
            case PROCESSED -> CallbackOutcome.PROCESSED;
            case ALREADY_APPLIED -> CallbackOutcome.ALREADY_APPLIED;
            case ORDER_NOT_FOUND -> CallbackOutcome.TARGET_NOT_FOUND;
            case IGNORED, DUPLICATE -> CallbackOutcome.IGNORED;
        };
    }

    private record Outcome(CallbackResult result, String note) {
    }
}
