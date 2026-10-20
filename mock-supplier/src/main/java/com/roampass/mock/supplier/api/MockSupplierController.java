package com.roampass.mock.supplier.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass.mock.supplier.issuance.IssueResult;
import com.roampass.mock.supplier.issuance.MockSupplierException;
import com.roampass.mock.supplier.issuance.MockSupplierService;
import com.roampass.mock.supplier.issuance.ProfileView;

import jakarta.validation.Valid;

/**
 * 부록 A 계약: 발급 접수 202, 프로필 조회, 시연용 상태 변경
 */
@RestController
@RequestMapping("/mock/supplier")
public class MockSupplierController {

    private final MockSupplierService service;
    private final Clock clock = Clock.systemUTC();

    public MockSupplierController(MockSupplierService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    public ResponseEntity<IssueResult> issue(@Valid @RequestBody IssueRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.issue(request.toCommand()));
    }

    @GetMapping("/profiles/{iccid}")
    public ProfileView profile(@PathVariable String iccid) {
        return service.findProfile(iccid).orElseThrow(() ->
                new MockSupplierException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Unknown ICCID"));
    }

    /** 시연용: 설치(INSTALLED)·개통(ENABLED)·삭제(DELETED) 흉내 */
    @PostMapping("/profiles/{iccid}/state")
    public ProfileView changeState(@PathVariable String iccid, @Valid @RequestBody ChangeStateRequest request) {
        return service.changeState(iccid, request.state(), Instant.now(clock));
    }
}
