package com.roampass_server.mysim.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.mysim.application.IssuanceWaiting.Action;
import com.roampass_server.mysim.application.IssuanceWaiting.Stage;
import com.roampass_server.mysim.application.MySimDetail.SimInfo;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 내 SIM 조회. 본인 주문만, 남의 주문은 없는 주문과 같은 404
 * 목록은 결제가 확정된 주문만(결제 대기·결제 없이 만료된 주문 제외), 행마다 프로필 추가 조회(페이지 크기 최대 50)
 * 발급 대기 결과는 클라이언트 폴링으로 전달(상세 응답에 대기 정보·권장 재조회 간격)
 */
@Transactional(readOnly = true)
public class MySimQueryService {

    /** 목록에 보이는 주문 상태: 결제 대기(PENDING)·결제 없이 만료(EXPIRED) 제외 */
    static final Set<OrderStatus> VISIBLE = EnumSet.complementOf(EnumSet.of(OrderStatus.PENDING, OrderStatus.EXPIRED));

    /** 발급 대기 화면의 권장 재조회 간격 */
    static final int POLL_INTERVAL_SECONDS = 3;

    private final OrderRepository orderRepository;
    private final SimProfileRepository profileRepository;
    private final IssuanceRequestRepository issuanceRequestRepository;
    private final Clock clock;
    private final Duration callbackTimeout;

    /** @param callbackTimeout 공급사 요청 후 결과 대기 한도(넘으면 서버가 실패 처리 후 자동 환불) */
    public MySimQueryService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                             IssuanceRequestRepository issuanceRequestRepository, Clock clock,
                             Duration callbackTimeout) {
        this.orderRepository = orderRepository;
        this.profileRepository = profileRepository;
        this.issuanceRequestRepository = issuanceRequestRepository;
        this.clock = clock;
        this.callbackTimeout = callbackTimeout;
    }

    public PageResult<MySimSummary> list(UserId userId, int page, int size) {
        return orderRepository.findByUser(userId, VISIBLE, page, size).map(order -> {
            SimProfile profile = profileRepository.findByOrderNo(order.orderNo()).orElse(null);
            return new MySimSummary(order.orderNo().value(), order.planId(), order.simType(), order.amount(),
                    order.travelStartDate(), order.status(), profile == null ? null : profile.effectiveStatus(Instant.now(clock)),
                    order.createdAt());
        });
    }

    public MySimDetail detail(UserId userId, OrderNo orderNo) {
        Instant now = Instant.now(clock);
        Order order = orderRepository.findByOrderNo(orderNo)
                .filter(found -> found.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        SimProfile profile = profileRepository.findByOrderNo(orderNo).orElse(null);
        boolean refundable = UserRefundPolicy.isRefundable(order, profile);
        return new MySimDetail(order.orderNo().value(), order.planId(), order.simType(), order.amount(),
                order.travelStartDate(), order.effectiveStatus(now), order.createdAt(), order.issuedAt(),
                refundable, profile == null ? null : simInfo(profile, now), waiting(order, refundable, now));
    }

    /** 이심 결제 후 발급 전에만 대기 정보, 그 외 null */
    private IssuanceWaiting waiting(Order order, boolean refundable, Instant now) {
        return switch (order.status()) {
            case PAID -> new IssuanceWaiting(Stage.QUEUED, null, null, false, POLL_INTERVAL_SECONDS,
                    actions(refundable));
            case ISSUE_REQUESTED -> {
                Instant requestedAt = issuanceRequestRepository.findLatestByOrderNo(order.orderNo())
                        .map(IssuanceRequest::requestedAt).orElse(null);
                Instant deadline = requestedAt == null ? null : requestedAt.plus(callbackTimeout);
                yield new IssuanceWaiting(Stage.REQUESTED, requestedAt, deadline,
                        deadline != null && now.isAfter(deadline), POLL_INTERVAL_SECONDS, actions(refundable));
            }
            default -> null;
        };
    }

    private static List<Action> actions(boolean refundable) {
        List<Action> actions = new ArrayList<>(List.of(Action.WAIT, Action.CHECK_LATER));
        if (refundable) {
            actions.add(Action.REQUEST_REFUND);
        }
        return List.copyOf(actions);
    }

    private static SimInfo simInfo(SimProfile p, Instant now) {
        boolean activated = p.activatedAt() != null;
        Duration remaining = p.remaining(now);
        return new SimInfo(p.iccid().value(), p.activationCode() == null ? null : p.activationCode().value(),
                p.effectiveStatus(now), p.usageDays(), p.activatedAt(),
                activated ? p.usagePeriod().expiresAt() : null, activated ? p.usagePeriod().expiresAtKst() : null,
                remaining == null ? null : remaining.toSeconds(), p.isDeleted());
    }
}
