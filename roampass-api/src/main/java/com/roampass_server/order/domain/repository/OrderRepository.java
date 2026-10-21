package com.roampass_server.order.domain.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderIdempotencyKey;
import com.roampass_server.order.domain.model.OrderStatus;

/**
 * 주문 저장소 포트. 구현은 infrastructure 계층
 */
public interface OrderRepository {

    /** 새 주문 저장. 주문번호 중복이면 실패(유니크 제약) */
    Order save(Order order);

    Optional<Order> findByOrderNo(OrderNo orderNo);

    /**
     * 멱등 키가 있는 새 주문 저장. 같은 사용자·같은 키의 주문이 이미 있으면 아무것도 하지 않음
     * 같은 키로 동시에 저장하면 나중 쪽은 앞 트랜잭션이 끝날 때까지 기다린 뒤 판정(DB 유니크 제약)
     *
     * @return 저장 여부. false면 같은 키의 주문이 이미 있음({@link #findByUserAndIdempotencyKey}로 조회)
     */
    boolean insertIfKeyAbsent(Order order);

    Optional<Order> findByUserAndIdempotencyKey(UserId userId, OrderIdempotencyKey key);

    /** PENDING이면서 만료 시각이 {@code now} 이전인 주문, 만료 시각 오름차순 최대 {@code limit}건 */
    List<Order> findExpirable(Instant now, int limit);

    /** 발급 요청 대상: PAID 상태의 이심 주문, 오래된 순 최대 {@code limit}건 */
    List<Order> findIssuable(int limit);

    /** 주어진 상태의 주문, 생성 시각 오름차순 최대 {@code limit}건(어드민 이상 건 조회) */
    List<Order> findByStatuses(Collection<OrderStatus> statuses, int limit);

    /** 어드민 주문 검색, 생성 시각 내림차순(최신 먼저) */
    PageResult<Order> search(OrderSearchCondition condition, int page, int size);

    /** 사용자 본인의 주문 중 주어진 상태인 것, 생성 시각 내림차순(최신 먼저) */
    PageResult<Order> findByUser(UserId userId, Collection<OrderStatus> statuses, int page, int size);

    /**
     * 저장된 상태가 아직 {@code expected}일 때만 주문의 변경을 저장(조건부 UPDATE)
     *
     * @return 저장 여부. false면 그 사이 다른 쪽이 상태를 바꾼 것
     */
    boolean saveIfStatus(Order order, OrderStatus expected);
}
