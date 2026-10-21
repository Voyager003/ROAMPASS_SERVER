package com.roampass_server.admin.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.admin.application.AdminOrderDetail;
import com.roampass_server.admin.application.AdminOrderQueryService;
import com.roampass_server.admin.application.AdminOrderSearchCondition;
import com.roampass_server.admin.application.AdminOrderSummary;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.PaymentStatus;

/**
 * 어드민 주문 조회 API. X-Admin-Id 필수(AdminAuthInterceptor)
 */
@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    static final int MAX_PAGE_SIZE = 50;

    private final AdminOrderQueryService queryService;
    private final Clock clock;

    public AdminOrderController(AdminOrderQueryService queryService, Clock clock) {
        this.queryService = queryService;
        this.clock = clock;
    }

    @GetMapping
    public AdminResponse<PageResult<AdminOrderSummary>> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) InstallStatus installStatus,
            @RequestParam(required = false) SimType simType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidValueException("INVALID_PAGE", "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        Instant asOf = Instant.now(clock);
        return new AdminResponse<>(asOf,
                queryService.search(new AdminOrderSearchCondition(status, paymentStatus, installStatus, simType, from, to),
                        page, size));
    }

    @GetMapping("/{orderNo}")
    public AdminResponse<AdminOrderDetail> detail(@PathVariable String orderNo) {
        Instant asOf = Instant.now(clock);
        return new AdminResponse<>(asOf, queryService.detail(OrderNo.of(orderNo)));
    }
}
