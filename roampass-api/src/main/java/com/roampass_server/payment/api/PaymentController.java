package com.roampass_server.payment.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.payment.application.PaymentService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 결제 API, X-User-Id 필수
 */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/orders/{orderNo}/pay")
    public PayResponse pay(UserId userId, @PathVariable String orderNo) {
        return PayResponse.from(paymentService.pay(userId, OrderNo.of(orderNo)));
    }
}
