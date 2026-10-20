package com.roampass_server.payment.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.payment.application.PaymentReconciliationService;

@DisplayName("응답 유실 정리 스케줄러")
class PaymentReconciliationSchedulerTest {

    private final PaymentReconciliationService service = mock(PaymentReconciliationService.class);
    private final PaymentReconciliationScheduler scheduler = new PaymentReconciliationScheduler(service);

    @Test
    @DisplayName("실행되면 정리 유스케이스를 한 번 호출한다")
    void delegates() {
        // given
        given(service.reconcile()).willReturn(1);

        // when
        scheduler.reconcile();

        // then
        verify(service).reconcile();
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다")
    void swallows_failure() {
        // given
        given(service.reconcile()).willThrow(new IllegalStateException("pg down"));

        // when // then
        assertThatCode(scheduler::reconcile).doesNotThrowAnyException();
    }
}
