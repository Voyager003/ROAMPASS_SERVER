package com.roampass_server.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * PaymentRepository의 JPA 구현. 상태 변경은 조건부 UPDATE로만 반영
 */
@Repository
@RequiredArgsConstructor
class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        try {
            return jpaRepository.saveAndFlush(PaymentJpaEntity.fromDomain(payment)).toDomain();
        } catch (DataIntegrityViolationException e) {
            // 새 시도에서 날 수 있는 위반은 멱등 키 유니크뿐(주문 존재는 호출 전에 확인): 동시 결제 요청
            throw new ConflictException("PAYMENT_IN_PROGRESS", "Another payment attempt was started concurrently");
        }
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(IdempotencyKey key) {
        return jpaRepository.findByIdempotencyKey(key.value()).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public List<Payment> findByOrderNo(OrderNo orderNo) {
        return jpaRepository.findByOrderNoOrderByRequestedAtAsc(orderNo.value()).stream()
                .map(PaymentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Payment> findStaleRequested(Instant requestedBefore, int limit) {
        return jpaRepository.findByStatusAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
                        PaymentStatus.REQUESTED, requestedBefore, PageRequest.of(0, limit)).stream()
                .map(PaymentJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean saveIfStatus(Payment payment, PaymentStatus expected) {
        int updated = jpaRepository.updateIfStatus(payment.idempotencyKey().value(), expected, payment.status(),
                payment.pgTid() == null ? null : payment.pgTid().value(), payment.cancelledAmount().amount(),
                payment.failureReason(), payment.approvedAt());
        return updated == 1;
    }
}
