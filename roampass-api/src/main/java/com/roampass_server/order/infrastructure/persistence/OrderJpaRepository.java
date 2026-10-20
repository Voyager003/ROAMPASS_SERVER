package com.roampass_server.order.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.order.domain.model.OrderStatus;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    Optional<OrderJpaEntity> findByOrderNo(String orderNo);

    // idx_orders_status_expires_at 인덱스 사용
    List<OrderJpaEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(OrderStatus status, Instant now,
                                                                                    Pageable pageable);

    /**
     * 저장된 상태가 expected일 때만 변경 반영(조건부 UPDATE), 영향 행 수 반환
     * 벌크 UPDATE라 영속성 컨텍스트를 비워 이후 조회가 오래된 엔티티를 보지 않게 함
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OrderJpaEntity o
               set o.status = :status, o.issuedAt = :issuedAt
             where o.orderNo = :orderNo and o.status = :expected
            """)
    int updateIfStatus(@Param("orderNo") String orderNo, @Param("expected") OrderStatus expected,
                       @Param("status") OrderStatus status, @Param("issuedAt") Instant issuedAt);
}
