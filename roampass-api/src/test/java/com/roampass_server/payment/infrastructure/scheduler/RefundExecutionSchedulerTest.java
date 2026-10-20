package com.roampass_server.payment.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.payment.application.RefundExecutionService;

@DisplayName("환불 실행 스케줄러")
class RefundExecutionSchedulerTest {

    private final RefundExecutionService service = mock(RefundExecutionService.class);
    private final RefundExecutionScheduler scheduler = new RefundExecutionScheduler(service);

    @Test
    @DisplayName("환불 실행을 호출하고, 실패해도 예외를 밖으로 던지지 않는다")
    void delegates_and_swallows() {
        // when
        scheduler.processDue();
        given(service.processDue()).willThrow(new IllegalStateException("pg down"));

        // then
        verify(service).processDue();
        assertThatCode(scheduler::processDue).doesNotThrowAnyException();
    }
}
