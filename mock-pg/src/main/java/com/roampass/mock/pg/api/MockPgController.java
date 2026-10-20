package com.roampass.mock.pg.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass.mock.pg.payment.CancelResult;
import com.roampass.mock.pg.payment.MockPgException;
import com.roampass.mock.pg.payment.MockPgService;
import com.roampass.mock.pg.payment.PaymentView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/mock/pg")
public class MockPgController {

    private final MockPgService service;

    public MockPgController(MockPgService service) {
        this.service = service;
    }

    @PostMapping("/approve")
    public PaymentView approve(@Valid @RequestBody ApproveRequest request) {
        return service.approve(request.toCommand());
    }

    @PostMapping("/cancel")
    public CancelResult cancel(@Valid @RequestBody CancelRequest request) {
        return service.cancel(request.toCommand());
    }

    @GetMapping("/payments/{idempotencyKey}")
    public PaymentView find(@PathVariable String idempotencyKey) {
        return service.find(idempotencyKey).orElseThrow(() ->
                new MockPgException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Unknown idempotency key"));
    }
}
