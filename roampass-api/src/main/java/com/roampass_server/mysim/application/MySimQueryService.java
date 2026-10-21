package com.roampass_server.mysim.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.mysim.application.MySimDetail.SimInfo;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 내 SIM 조회. 본인 주문만, 남의 주문은 없는 주문과 같은 404
 * 목록은 결제가 확정된 주문만(결제 대기·결제 없이 만료된 주문 제외), 행마다 프로필 추가 조회(페이지 크기 최대 50)
 */
@Transactional(readOnly = true)
public class MySimQueryService {

    /** 목록에 보이는 주문 상태: 결제 대기(PENDING)·결제 없이 만료(EXPIRED) 제외 */
    static final Set<OrderStatus> VISIBLE = EnumSet.complementOf(EnumSet.of(OrderStatus.PENDING, OrderStatus.EXPIRED));

    private final OrderRepository orderRepository;
    private final SimProfileRepository profileRepository;
    private final Clock clock;

    public MySimQueryService(OrderRepository orderRepository, SimProfileRepository profileRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    public PageResult<MySimSummary> list(UserId userId, int page, int size) {
        return orderRepository.findByUser(userId, VISIBLE, page, size).map(order -> {
            SimProfile profile = profileRepository.findByOrderNo(order.orderNo()).orElse(null);
            return new MySimSummary(order.orderNo().value(), order.planId(), order.simType(), order.amount(),
                    order.travelStartDate(), order.status(), profile == null ? null : profile.status(),
                    order.createdAt());
        });
    }

    public MySimDetail detail(UserId userId, OrderNo orderNo) {
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        SimProfile profile = profileRepository.findByOrderNo(orderNo).orElse(null);
        return new MySimDetail(order.orderNo().value(), order.planId(), order.simType(), order.amount(),
                order.travelStartDate(), order.effectiveStatus(now), order.createdAt(), order.issuedAt(),
                UserRefundPolicy.isRefundable(order, profile), profile == null ? null : simInfo(profile, now));
    }

    private static SimInfo simInfo(SimProfile p, Instant now) {
        boolean activated = p.activatedAt() != null;
        Duration remaining = p.remaining(now);
        return new SimInfo(p.iccid().value(), p.activationCode() == null ? null : p.activationCode().value(),
                p.status(), p.usageDays(), p.activatedAt(),
                activated ? p.usagePeriod().expiresAt() : null, activated ? p.usagePeriod().expiresAtKst() : null,
                remaining == null ? null : remaining.toSeconds(), p.isDeleted());
    }
}
