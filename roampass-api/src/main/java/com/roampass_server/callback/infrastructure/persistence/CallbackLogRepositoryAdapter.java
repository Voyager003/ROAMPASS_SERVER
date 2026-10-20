package com.roampass_server.callback.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.common.domain.OrderNo;

import lombok.RequiredArgsConstructor;

/**
 * CallbackLogRepository의 JPA 구현. 중복 판정은 PostgreSQL ON CONFLICT DO NOTHING
 * (예외 없이 판정해야 같은 트랜잭션의 후속 처리가 가능, 유니크 위반 예외는 PostgreSQL 트랜잭션을 중단시킴)
 */
@Repository
@RequiredArgsConstructor
class CallbackLogRepositoryAdapter implements CallbackLogRepository {

    private final CallbackLogJpaRepository jpaRepository;

    @Override
    @Transactional
    public boolean insertIfAbsent(CallbackLog log) {
        return jpaRepository.insertIfAbsent(log.source().name(), log.eventKey(),
                log.orderNo() == null ? null : log.orderNo().value(), log.signatureOk(), log.payload(),
                log.outcome().name(), log.note(), log.receivedAt()) == 1;
    }

    @Override
    @Transactional
    public void save(CallbackLog log) {
        jpaRepository.save(CallbackLogJpaEntity.fromDomain(log));
    }

    @Override
    @Transactional
    public void updateOutcome(String eventKey, CallbackOutcome outcome, String note) {
        jpaRepository.updateOutcome(eventKey, outcome, note);
    }

    @Override
    public Optional<CallbackLog> findByEventKey(String eventKey) {
        return jpaRepository.findByEventKey(eventKey).map(CallbackLogJpaEntity::toDomain);
    }

    @Override
    public List<CallbackLog> findRecentByOutcome(CallbackOutcome outcome, int limit) {
        return jpaRepository.findByOutcomeOrderByReceivedAtDescIdDesc(outcome,
                PageRequest.of(0, limit)).stream()
                .map(CallbackLogJpaEntity::toDomain).toList();
    }

    @Override
    public List<CallbackLog> findByOrderNo(OrderNo orderNo) {
        return jpaRepository.findByOrderNoOrderByReceivedAtAscIdAsc(orderNo.value()).stream()
                .map(CallbackLogJpaEntity::toDomain).toList();
    }
}
