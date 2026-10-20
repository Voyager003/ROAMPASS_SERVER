package com.roampass_server.payment.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.repository.PaymentRepository;

/**
 * 테스트용 인메모리 PaymentRepository. 멱등 키를 키로 보관
 */
public class FakePaymentRepository implements PaymentRepository {

    private final Map<IdempotencyKey, Payment> payments = new LinkedHashMap<>();

    @Override
    public synchronized Payment save(Payment payment) {
        if (payments.containsKey(payment.idempotencyKey())) {
            throw new ConflictException("PAYMENT_IN_PROGRESS", "duplicate idempotency key");
        }
        payments.put(payment.idempotencyKey(), payment);
        return payment;
    }

    @Override
    public synchronized Optional<Payment> findByIdempotencyKey(IdempotencyKey key) {
        return Optional.ofNullable(payments.get(key));
    }

    @Override
    public synchronized List<Payment> findByOrderNo(OrderNo orderNo) {
        return payments.values().stream()
                .filter(p -> p.orderNo().equals(orderNo))
                .sorted(Comparator.comparing(Payment::requestedAt))
                .toList();
    }

    @Override
    public synchronized boolean saveIfStatus(Payment payment, PaymentStatus expected) {
        Payment current = payments.get(payment.idempotencyKey());
        if (current == null || current.status() != expected) {
            return false;
        }
        payments.put(payment.idempotencyKey(), payment);
        return true;
    }

    public synchronized int count() {
        return payments.size();
    }
}
