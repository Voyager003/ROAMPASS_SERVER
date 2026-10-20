package com.roampass_server.order.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.application.OrderResult;
import com.roampass_server.order.application.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 주문 API, X-User-Id 필수
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(UserId userId, @Valid @RequestBody CreateOrderRequest request) {
        OrderResult result = orderService.createOrder(userId, request.planId(), request.travelStartDate());
        URI location = URI.create("/api/orders/" + result.orderNo().value());
        return ResponseEntity.created(location).body(OrderResponse.from(result));
    }

    @GetMapping("/{orderNo}")
    public OrderResponse get(UserId userId, @PathVariable String orderNo) {
        return OrderResponse.from(orderService.getMyOrder(userId, OrderNo.of(orderNo)));
    }
}
