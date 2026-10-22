package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.application.exception.UpstreamUnavailableException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierDemoClient;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 시연용: 실제 기기 없이 설치·개통·유심 수령을 흉내(과제 "설치·개통은 모의 공급사의 상태를 바꾸는 것으로 흉내").
 * 공급사 상태를 바꾼 뒤 그 응답(바뀐 프로필 상태)을 폴링과 같은 반영 경로(ProfileSignalApplier)로 즉시 반영.
 * 우리 쪽 상태를 직접 바꾸지 않으므로 운영 흐름(폴링·개통 콜백)과 결과가 같음. 트랜잭션 밖에서 외부 호출
 */
@Slf4j
public class DemoSimService {

    static final String SOURCE_DEMO = "시연 상태 변경";

    private static final Set<InstallStatus> INSTALLABLE = EnumSet.of(InstallStatus.ISSUED, InstallStatus.QR_VIEWED);
    private static final Set<InstallStatus> ACTIVATABLE = EnumSet.of(InstallStatus.ISSUED, InstallStatus.QR_VIEWED,
            InstallStatus.INSTALLED);

    private final OrderRepository orderRepository;
    private final SimProfileRepository profileRepository;
    private final SupplierDemoClient demoClient;
    private final ProfileSignalApplier applier;
    private final Clock clock;

    public DemoSimService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                          SupplierDemoClient demoClient, ProfileSignalApplier applier, Clock clock) {
        this.orderRepository = orderRepository;
        this.profileRepository = profileRepository;
        this.demoClient = demoClient;
        this.applier = applier;
        this.clock = clock;
    }

    /** 이심 설치 흉내(공급사 INSTALLED). 발급 완료 주문의 발급됨·QR 열람 상태에서만 */
    public InstallStatus install(UserId userId, OrderNo orderNo) {
        return changeEsimState(userId, orderNo, SupplierProfileState.INSTALLED, INSTALLABLE);
    }

    /** 이심 현지 개통 흉내(공급사 ENABLED). 설치 전이어도 가능(설치 후 바로 개통되는 기기와 같음) */
    public InstallStatus activate(UserId userId, OrderNo orderNo) {
        return changeEsimState(userId, orderNo, SupplierProfileState.ENABLED, ACTIVATABLE);
    }

    /**
     * 공항에서 받는 실물 유심 카드 대신 공급사가 이 주문에 내줄 카드 번호(ICCID). 등록은 사용자가 따로 함
     *
     * @throws ConflictException DEMO_NOT_APPLICABLE: 유심 주문이 아니거나 수령 대기(PICKUP_READY)가 아님
     */
    public String pickupCard(UserId userId, OrderNo orderNo) {
        Order order = ownedOrder(userId, orderNo);
        if (order.simType() != SimType.USIM || order.status() != OrderStatus.PICKUP_READY) {
            throw notApplicable();
        }
        String iccid = demoClient.issuePickupCard(orderNo)
                .orElseThrow(() -> new UpstreamUnavailableException("SUPPLIER_UNAVAILABLE",
                        "Supplier did not respond, try again"));
        log.info("event=demo_pickup_card orderNo={}", orderNo.value());
        return iccid;
    }

    private InstallStatus changeEsimState(UserId userId, OrderNo orderNo, SupplierProfileState target,
                                          Set<InstallStatus> allowed) {
        Order order = ownedOrder(userId, orderNo);
        if (order.simType() != SimType.ESIM) {
            throw notApplicable();
        }
        SimProfile profile = profileRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("SIM_NOT_FOUND", "SIM is not issued yet"));
        InstallStatus before = profile.effectiveStatus(Instant.now(clock));
        // 환불 중·환불된 주문이나 기기에서 삭제된 SIM은 설치·개통 흉내 대상이 아님
        if (order.status() != OrderStatus.ISSUED || profile.isDeleted() || !allowed.contains(before)) {
            throw notApplicable();
        }
        SupplierProfileResult result = demoClient.changeProfileState(profile.iccid(), target);
        if (result.outcome() != SupplierProfileResult.Outcome.FOUND) {
            throw new UpstreamUnavailableException("SUPPLIER_UNAVAILABLE", "Supplier did not respond, try again");
        }
        applier.apply(profile, result, SOURCE_DEMO);
        InstallStatus after = profileRepository.findByOrderNo(orderNo).orElseThrow()
                .effectiveStatus(Instant.now(clock));
        log.info("event=demo_sim_state orderNo={} requested={} from={} to={}", orderNo.value(), target, before, after);
        return after;
    }

    private Order ownedOrder(UserId userId, OrderNo orderNo) {
        return orderRepository.findByOrderNo(orderNo).filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));
    }

    private static ConflictException notApplicable() {
        return new ConflictException("DEMO_NOT_APPLICABLE", "This demo action is not available for the SIM's current state");
    }
}
