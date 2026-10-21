package com.roampass_server.order.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.domain.model.OrderStatus;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    Optional<OrderJpaEntity> findByOrderNo(String orderNo);

    // uq_orders_user_idempotency_key 인덱스 사용
    Optional<OrderJpaEntity> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    /**
     * 같은 사용자·같은 멱등 키의 주문이 없을 때만 INSERT, 이미 있으면 아무것도 하지 않고 0 반환
     * 같은 키를 동시에 넣으면 나중 쪽은 앞 트랜잭션이 끝날 때까지 대기 후 판정(커밋이면 0, 롤백이면 1)
     * 예외 대신 영향 행 수로 판정해 트랜잭션을 깨지 않음(callback_log와 같은 방식)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into orders (order_no, user_id, plan_id, sim_type, amount, currency, travel_start_date, status,
                                created_at, expires_at, issued_at, idempotency_key)
            values (:orderNo, :userId, :planId, :simType, :amount, :currency, :travelStartDate, :status,
                    :createdAt, :expiresAt, :issuedAt, :idempotencyKey)
            on conflict (user_id, idempotency_key) where idempotency_key is not null do nothing
            """, nativeQuery = true)
    int insertIfKeyAbsent(@Param("orderNo") String orderNo, @Param("userId") String userId,
                          @Param("planId") Long planId, @Param("simType") String simType,
                          @Param("amount") long amount, @Param("currency") String currency,
                          @Param("travelStartDate") LocalDate travelStartDate, @Param("status") String status,
                          @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt,
                          @Param("issuedAt") Instant issuedAt, @Param("idempotencyKey") String idempotencyKey);

    // idx_orders_status_expires_at 인덱스 사용
    List<OrderJpaEntity> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(OrderStatus status, Instant now,
                                                                                    Pageable pageable);

    List<OrderJpaEntity> findByStatusInOrderByCreatedAtAsc(Collection<OrderStatus> statuses,
                                                           Pageable pageable);

    // 내 SIM 목록, idx_orders_user_created_at 인덱스 사용
    Page<OrderJpaEntity> findByUserIdAndStatusIn(String userId, Collection<OrderStatus> statuses, Pageable pageable);

    // 발급 요청 대상(PAID 이심), 오래된 순
    List<OrderJpaEntity> findByStatusAndSimTypeOrderByCreatedAtAsc(OrderStatus status,
                                                                   SimType simType,
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
