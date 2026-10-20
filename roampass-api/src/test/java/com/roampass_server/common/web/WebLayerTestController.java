package com.roampass_server.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.UserId;

/**
 * Test-only endpoints that exercise the common web layer
 * (header resolvers, admin interceptor, exception handler) without any real feature.
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
