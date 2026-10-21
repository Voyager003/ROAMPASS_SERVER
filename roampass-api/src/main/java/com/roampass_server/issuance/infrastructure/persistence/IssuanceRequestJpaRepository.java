package com.roampass_server.issuance.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.issuance.domain.model.IssuanceStatus;

interface IssuanceRequestJpaRepository extends JpaRepository<IssuanceRequestJpaEntity, Long> {

    Optional<IssuanceRequestJpaEntity> findFirstByOrderNoOrderByAttemptDesc(String orderNo);

    int countByOrderNo(String orderNo);

    List<IssuanceRequestJpaEntity> findByOrderNoOrderByAttemptAsc(String orderNo);

    // idx_issuance_request_status_requested 인덱스 사용
    List<IssuanceRequestJpaEntity> findByStatusOrderByRequestedAtAsc(IssuanceStatus status, Pageable pageable);

    List<IssuanceRequestJpaEntity> findByStatusInAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
            Collection<IssuanceStatus> statuses, Instant requestedBefore, Pageable pageable);

    /**
     * 저장된 상태가 expected일 때만 변경 반영(조건부 UPDATE), 영향 행 수 반환
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update IssuanceRequestJpaEntity r
               set r.status = :status, r.supplierOrderNo = :supplierOrderNo, r.failureReason = :failureReason,
                   r.completedAt = :completedAt
             where r.id = :id and r.status = :expected
            """)
    int updateIfStatus(@Param("id") Long id, @Param("expected") IssuanceStatus expected,
                       @Param("status") IssuanceStatus status, @Param("supplierOrderNo") String supplierOrderNo,
                       @Param("failureReason") String failureReason, @Param("completedAt") Instant completedAt);
}
