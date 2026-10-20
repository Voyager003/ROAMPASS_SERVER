package com.roampass_server.history.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.StatusHistory;

@DisplayName("StatusHistoryRecorder")
class StatusHistoryRecorderTest {

    private enum SampleStatus { PENDING, EXPIRED }

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");
    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    @Test
    @DisplayName("상태 변경을 기록하면 주문번호로 조회할 수 있다")
    void records_and_reads_back() {
        // given
        FakeStatusHistoryRepository repository = new FakeStatusHistoryRepository();
        StatusHistoryRecorder recorder = new StatusHistoryRecorder(repository);

        // when
        recorder.record(ORDER_NO, HistoryTarget.order(ORDER_NO), SampleStatus.PENDING, SampleStatus.EXPIRED,
                Actor.system(), "결제 대기 만료", NOW);

        // then
        List<StatusHistory> histories = repository.findByOrderNo(ORDER_NO);
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).toStatus()).isEqualTo("EXPIRED");
        assertThat(histories.get(0).actor()).isEqualTo(Actor.system());
    }
}
