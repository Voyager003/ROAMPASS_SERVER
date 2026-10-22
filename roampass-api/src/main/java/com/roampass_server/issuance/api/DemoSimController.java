package com.roampass_server.issuance.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.DemoSimService;

import lombok.RequiredArgsConstructor;

/**
 * 시연용 SIM API(roampass.demo.enabled=true 일 때만 등록), X-User-Id 필수
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "roampass.demo", name = "enabled", havingValue = "true")
public class DemoSimController {

    private final DemoSimService demoSimService;

    /** 기기에 설치한 것처럼(공급사 INSTALLED) */
    @PostMapping("/api/sims/{orderNo}/demo/install")
    public DemoSimStateResponse install(UserId userId, @PathVariable String orderNo) {
        return new DemoSimStateResponse(demoSimService.install(userId, OrderNo.of(orderNo)).name());
    }

    /** 현지에서 개통한 것처럼(공급사 ENABLED) */
    @PostMapping("/api/sims/{orderNo}/demo/activate")
    public DemoSimStateResponse activate(UserId userId, @PathVariable String orderNo) {
        return new DemoSimStateResponse(demoSimService.activate(userId, OrderNo.of(orderNo)).name());
    }

    /** 공항에서 유심 카드를 받은 것처럼: 공급사가 내준 카드 번호(ICCID) */
    @PostMapping("/api/orders/{orderNo}/demo/pickup-card")
    public PickupCardResponse pickupCard(UserId userId, @PathVariable String orderNo) {
        return new PickupCardResponse(demoSimService.pickupCard(userId, OrderNo.of(orderNo)));
    }

    public record DemoSimStateResponse(String status) {
    }

    public record PickupCardResponse(String iccid) {
    }
}
