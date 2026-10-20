package com.roampass_server.history.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface StatusHistoryJpaRepository extends JpaRepository<StatusHistoryJpaEntity, Long> {

    // idx_status_history_order_occurred 인덱스 사용, 같은 시각이면 저장 순서(id)
    List<StatusHistoryJpaEntity> findByOrderNoOrderByOccurredAtAscIdAsc(String orderNo);
}
