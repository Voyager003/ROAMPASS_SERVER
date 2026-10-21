package com.roampass_server.admin.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.application.FakeOrderRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.repository.PaymentRepository;

/**
 * 테스트용 어드민 검색. 인메모리 저장소들을 같은 규칙(최근 결제 시도 상태, 조회 시점 설치 상태)으로 거름
 */
public class FakeAdminOrderSearch implements AdminOrderSearch {

    private final FakeOrderRepository orders;
    private final PaymentRepository payments;
    private final SimProfileRepository profiles;

    public FakeAdminOrderSearch(FakeOrderRepository orders, PaymentRepository payments, SimProfileRepository profiles) {
        this.orders = orders;
        this.payments = payments;
        this.profiles = profiles;
    }

    @Override
    public PageResult<OrderNo> search(AdminOrderSearchCondition c, Instant now, int page, int size) {
        List<Order> matched = orders.findByStatuses(EnumSet.allOf(OrderStatus.class), Integer.MAX_VALUE).stream()
                .filter(o -> c.status() == null || o.status() == c.status())
                .filter(o -> c.simType() == null || o.simType() == c.simType())
                .filter(o -> c.createdFrom() == null || !o.createdAt().isBefore(c.createdFrom()))
                .filter(o -> c.createdTo() == null || o.createdAt().isBefore(c.createdTo()))
                .filter(o -> c.paymentStatus() == null || c.paymentStatus() == latestPaymentStatus(o))
                .filter(o -> c.installStatus() == null || profiles.findByOrderNo(o.orderNo())
                        .map(p -> p.effectiveStatus(now) == c.installStatus()).orElse(false))
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();
        List<OrderNo> content = matched.stream().skip((long) page * size).limit(size).map(Order::orderNo).toList();
        return new PageResult<>(content, page, size, matched.size());
    }

    private PaymentStatus latestPaymentStatus(Order order) {
        return payments.findByOrderNo(order.orderNo()).stream()
                .max(Comparator.comparing(Payment::requestedAt))
                .map(Payment::status)
                .orElse(null);
    }
}
