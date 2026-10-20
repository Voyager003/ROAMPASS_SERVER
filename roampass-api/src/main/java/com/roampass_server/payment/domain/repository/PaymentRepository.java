package com.roampass_server.payment.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;

/**
 * 결제 저장소 포트
 */
public interface PaymentRepository {

    /** 새 결제 시도 저장. 같은 멱등 키가 이미 있으면 409 PAYMENT_IN_PROGRESS(동시 결제 요청) */
    Payment save(Payment payment);

    Optional<Payment> findByIdempotencyKey(IdempotencyKey key);

    /** 주문의 결제 시도 전체, 요청 시각 오름차순 */
    List<Payment> findByOrderNo(OrderNo orderNo);

    /** {@code requestedBefore} 이전에 요청되어 아직 REQUESTED인 결제, 요청 시각 오름차순 최대 {@code limit}건 */
    List<Payment> findStaleRequested(Instant requestedBefore, int limit);

    /**
     * 저장된 상태가 아직 {@code expected}일 때만 결제의 변경을 저장(조건부 UPDATE)
     *
     * @return 저장 여부. false면 그 사이 다른 통지가 먼저 반영
     */
    boolean saveIfStatus(Payment payment, PaymentStatus expected);
}
