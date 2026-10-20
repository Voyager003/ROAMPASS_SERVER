package com.roampass_server.callback.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.callback.domain.model.CallbackOutcome;

interface CallbackLogJpaRepository extends JpaRepository<CallbackLogJpaEntity, Long> {

    Optional<CallbackLogJpaEntity> findByEventKey(String eventKey);

    /**
     * event_key가 처음일 때만 INSERT, 이미 있으면 아무것도 하지 않고 0 반환
     * 같은 키를 동시에 넣으면 나중 쪽은 앞 트랜잭션이 끝날 때까지 대기 후 판정(커밋이면 0, 롤백이면 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into callback_log (source, event_key, signature_ok, payload, outcome, note, received_at)
            values (:source, :eventKey, :signatureOk, :payload, :outcome, :note, :receivedAt)
            on conflict (event_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("source") String source, @Param("eventKey") String eventKey,
                       @Param("signatureOk") boolean signatureOk, @Param("payload") String payload,
                       @Param("outcome") String outcome, @Param("note") String note,
                       @Param("receivedAt") Instant receivedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CallbackLogJpaEntity c set c.outcome = :outcome, c.note = :note where c.eventKey = :eventKey")
    int updateOutcome(@Param("eventKey") String eventKey, @Param("outcome") CallbackOutcome outcome,
                      @Param("note") String note);
}
