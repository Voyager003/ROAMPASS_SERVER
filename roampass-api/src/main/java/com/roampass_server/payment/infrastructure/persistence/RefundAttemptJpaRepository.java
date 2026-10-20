package com.roampass_server.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.payment.domain.model.RefundStatus;

interface RefundAttemptJpaRepository extends JpaRepository<RefundAttemptJpaEntity, Long> {

    // idx_refund_attempt_status_next 인덱스 사용
    List<RefundAttemptJpaEntity> findByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(RefundStatus status,
                                                                                             Instant now,
                                                                                             Pageable pageable);

    List<RefundAttemptJpaEntity> findByOrderNoOrderByCreatedAtAsc(String orderNo);

    /**
     * 아직 PENDING이고 시도 횟수가 기대값일 때만 변경 반영(조건부 UPDATE), 영향 행 수 반환
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefundAttemptJpaEntity r
               set r.status = :status, r.attemptCount = :attemptCount, r.nextRetryAt = :nextRetryAt,
                   r.lastError = :lastError, r.completedAt = :completedAt
             where r.id = :id and r.status = com.roampass_server.payment.domain.model.RefundStatus.PENDING
               and r.attemptCount = :expectedAttemptCount
            """)
    int updateIfPending(@Param("id") Long id, @Param("expectedAttemptCount") int expectedAttemptCount,
                        @Param("status") RefundStatus status, @Param("attemptCount") int attemptCount,
                        @Param("nextRetryAt") Instant nextRetryAt, @Param("lastError") String lastError,
                        @Param("completedAt") Instant completedAt);
}
