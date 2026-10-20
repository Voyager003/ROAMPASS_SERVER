package com.roampass_server.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.UserId;

/**
 * 공통 웹 계층(헤더 리졸버, 어드민 인터셉터, 예외 핸들러)을 실제 기능 없이 검증하기 위한
 * 테스트 전용 엔드포인트
 */
@RestController
class WebLayerTestController {

    @GetMapping("/api/test/public")
    String publicEndpoint() {
        return "ok";
    }

    @GetMapping("/api/test/me")
    String currentUser(UserId userId) {
        return userId.value();
    }

    @GetMapping("/admin/test/me")
    String currentAdmin(AdminId adminId) {
        return adminId.value();
    }

    @GetMapping("/admin/test/no-param")
    String adminEndpointWithoutParameter() {
        return "ok";
    }

    @GetMapping("/api/test/invalid-value")
    void invalidValue() {
        throw new InvalidValueException("INVALID_SAMPLE", "sample is invalid");
    }

    @GetMapping("/api/test/illegal-transition")
    void illegalTransition() {
        throw new IllegalStateTransitionException("Order", "PENDING", "ISSUED");
    }

    @GetMapping("/api/test/unexpected")
    void unexpected() {
        throw new IllegalStateException("internal detail must not leak");
    }
}
