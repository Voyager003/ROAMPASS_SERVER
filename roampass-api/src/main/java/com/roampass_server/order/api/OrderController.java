package com.roampass_server.order.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.application.OrderResult;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderIdempotencyKey;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 주문 API, X-User-Id 필수
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final OrderService orderService;

    /**
     * 주문 생성. {@code Idempotency-Key}(선택)가 있으면 같은 키의 재전송에 처음 주문을 그대로 돌려줌(같은 201)
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(UserId userId,
                                                @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderIdempotencyKey key = idempotencyKey == null ? null : OrderIdempotencyKey.of(idempotencyKey);
        OrderResult result = orderService.createOrder(userId, request.planId(), request.travelStartDate(), key);
        URI location = URI.create("/api/orders/" + result.orderNo().value());
        return ResponseEntity.created(location).body(OrderResponse.from(result));
    }

    @GetMapping("/{orderNo}")
    public OrderResponse get(UserId userId, @PathVariable String orderNo) {
        return OrderResponse.from(orderService.getMyOrder(userId, OrderNo.of(orderNo)));
    }
}
