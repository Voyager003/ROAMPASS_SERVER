package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * QR 열람 기록(tx). 우리 서버가 직접 아는 유일한 설치 관련 신호지만 "설치"는 아님(환불 가능 판정에 영향 없음)
 */
public class QrViewService {

    private final OrderRepository orderRepository;
    private final SimProfileRepository profileRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public QrViewService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                         StatusHistoryRecorder historyRecorder, Clock clock) {
        this.orderRepository = orderRepository;
        this.profileRepository = profileRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /**
     * @throws NotFoundException 주문이 없거나 남의 주문(ORDER_NOT_FOUND), 아직 발급 전(SIM_NOT_FOUND)
     */
    @Transactional
    public void markViewed(UserId userId, OrderNo orderNo) {
        orderRepository.findByOrderNo(orderNo).filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
        SimProfile profile = profileRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("SIM_NOT_FOUND", "SIM is not issued yet"));
        Instant now = Instant.now(clock);
        SimProfile viewed = profile.markQrViewed(now);
        if (viewed != profile && profileRepository.saveIfStatus(viewed, profile.status())) {
            historyRecorder.record(orderNo, HistoryTarget.of(TargetType.SIM_PROFILE, profile.iccid().value()),
                    profile.status(), viewed.status(), Actor.user(userId), "QR 열람", now);
        }
    }
}
