package com.roampass_server.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.payment.domain.model.PaymentStatus;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    // idx_payment_order_requested 인덱스 사용
    List<PaymentJpaEntity> findByOrderNoOrderByRequestedAtAsc(String orderNo);

    // idx_payment_status_requested 인덱스 사용
    List<PaymentJpaEntity> findByStatusAndRequestedAtLessThanEqualOrderByRequestedAtAsc(PaymentStatus status,
                                                                                         Instant requestedBefore,
                                                                                         Pageable pageable);

    /**
     * 저장된 상태가 expected일 때만 변경 반영(조건부 UPDATE), 영향 행 수 반환
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PaymentJpaEntity p
               set p.status = :status, p.pgTid = :pgTid, p.cancelledAmount = :cancelledAmount,
                   p.failureReason = :failureReason, p.approvedAt = :approvedAt
             where p.idempotencyKey = :idempotencyKey and p.status = :expected
            """)
    int updateIfStatus(@Param("idempotencyKey") String idempotencyKey, @Param("expected") PaymentStatus expected,
                       @Param("status") PaymentStatus status, @Param("pgTid") String pgTid,
                       @Param("cancelledAmount") long cancelledAmount, @Param("failureReason") String failureReason,
                       @Param("approvedAt") Instant approvedAt);
}
