package com.roampass_server.callback.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@Transactional
@DisplayName("CallbackLogRepository (PostgreSQL)")
class CallbackLogRepositoryAdapterIntegrationTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-CBLG0001");

    @Autowired
    private CallbackLogRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("같은 eventKey는 처음 한 번만 저장되고, 저장한 값이 그대로 돌아온다")
    void inserts_once_per_event_key() {
        // given
        CallbackLog log = CallbackLog.received(CallbackSource.PG, "PG:evt_db_1", ORDER_NO, "{\"a\":1}", NOW);

        // when
        boolean first = repository.insertIfAbsent(log);
        boolean second = repository.insertIfAbsent(log);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        CallbackLog found = repository.findByEventKey("PG:evt_db_1").orElseThrow();
        assertThat(found.id()).isNotNull();
        assertThat(found.source()).isEqualTo(CallbackSource.PG);
        assertThat(found.signatureOk()).isTrue();
        assertThat(found.payload()).isEqualTo("{\"a\":1}");
        assertThat(found.outcome()).isEqualTo(CallbackOutcome.RECEIVED);
        assertThat(found.receivedAt()).isEqualTo(NOW);
        assertThat(found.orderNo()).isEqualTo(ORDER_NO);
        assertThat(repository.findByOrderNo(ORDER_NO)).extracting(CallbackLog::eventKey).contains("PG:evt_db_1");
    }

    @Test
    @DisplayName("처리 결과와 메모를 갱신한다")
    void updates_outcome() {
        // given
        repository.insertIfAbsent(CallbackLog.received(CallbackSource.PG, "PG:evt_db_2", null, "{}", NOW));

        // when
        repository.updateOutcome("PG:evt_db_2", CallbackOutcome.AMOUNT_MISMATCH, "AMOUNT_MISMATCH");

        // then
        assertThat(repository.findByEventKey("PG:evt_db_2")).get().satisfies(found -> {
            assertThat(found.outcome()).isEqualTo(CallbackOutcome.AMOUNT_MISMATCH);
            assertThat(found.note()).isEqualTo("AMOUNT_MISMATCH");
        });
    }

    @Test
    @DisplayName("eventKey 없는 서명 실패 기록은 여러 건 저장된다 (유니크 제약 대상 아님)")
    void stores_rejected_logs_without_key() {
        // given
        Integer before = jdbcTemplate.queryForObject(
                "select count(*) from callback_log where event_key is null", Integer.class);

        // when
        repository.save(CallbackLog.signatureRejected(CallbackSource.PG, "forged-1", NOW));
        repository.save(CallbackLog.signatureRejected(CallbackSource.PG, "forged-2", NOW));

        // then
        Integer after = jdbcTemplate.queryForObject(
                "select count(*) from callback_log where event_key is null and signature_ok = false", Integer.class);
        assertThat(after - before).isEqualTo(2);
    }
}
