package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.plan.application.PlanQueryService;

/**
 * 유심 ICCID 등록(tx): 주문 PICKUP_READY → ISSUED(조건부 UPDATE) + 유심 프로필 저장
 * 주문 상태를 먼저 바꿔 같은 주문의 동시 등록은 한쪽만 통과, 같은 ICCID의 동시 등록은 DB 유니크 제약이 막음
 */
public class UsimRegistrationStartService {

    private final OrderRepository orderRepository;
    private final SimProfileRepository profileRepository;
    private final PlanQueryService planQueryService;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public UsimRegistrationStartService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                                        PlanQueryService planQueryService, StatusHistoryRecorder historyRecorder,
                                        Clock clock) {
        this.orderRepository = orderRepository;
        this.profileRepository = profileRepository;
        this.planQueryService = planQueryService;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /**
     * @throws NotFoundException 주문이 없거나 남의 주문(ORDER_NOT_FOUND)
     * @throws com.roampass_server.common.domain.exception.InvalidValueException ICCID 형식·체크 디지트 오류(INVALID_ICCID)
     * @throws ConflictException 수령 대기 유심 주문이 아님(ORDER_NOT_REGISTRABLE), 이미 등록된 ICCID(ICCID_ALREADY_REGISTERED)
     */
    @Transactional
    public SimProfile register(UserId userId, OrderNo orderNo, String rawIccid) {
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNo(orderNo).filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        Iccid iccid = Iccid.of(rawIccid);
        if (order.status() != OrderStatus.PICKUP_READY) {
            throw new ConflictException("ORDER_NOT_REGISTRABLE", "Order is " + order.status());
        }
        if (profileRepository.existsByIccid(iccid)) {
            throw new ConflictException("ICCID_ALREADY_REGISTERED", "ICCID is already registered");
        }
        if (!orderRepository.saveIfStatus(order.markIssued(now), OrderStatus.PICKUP_READY)) {
            throw new ConflictException("ORDER_NOT_REGISTRABLE", "Order was registered concurrently");
        }
        int usageDays = planQueryService.getPlan(order.planId()).spec().days();
        SimProfile profile = profileRepository.save(SimProfile.registerUsim(orderNo, iccid, usageDays, now));
        historyRecorder.record(orderNo, HistoryTarget.order(orderNo), OrderStatus.PICKUP_READY, OrderStatus.ISSUED,
                Actor.user(userId), "유심 ICCID 등록", now);
        historyRecorder.record(orderNo, HistoryTarget.of(TargetType.SIM_PROFILE, iccid.value()), null,
                InstallStatus.ISSUED, Actor.user(userId), "유심 등록", now);
        return profile;
    }
}
