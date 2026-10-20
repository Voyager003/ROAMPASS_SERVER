package com.roampass_server.issuance.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.issuance.application.IssuanceDispatchService;
import com.roampass_server.issuance.application.IssuanceTimeoutService;

@DisplayName("발급 스케줄러")
class IssuanceSchedulersTest {

    private final IssuanceDispatchService dispatchService = mock(IssuanceDispatchService.class);
    private final IssuanceTimeoutService timeoutService = mock(IssuanceTimeoutService.class);
    private final IssuanceSchedulers schedulers = new IssuanceSchedulers(dispatchService, timeoutService);

    @Test
    @DisplayName("발급 요청·대기 초과 검사를 각각 호출한다")
    void delegates() {
        // when
        schedulers.dispatch();
        schedulers.failOverdue();

        // then
        verify(dispatchService).dispatch();
        verify(timeoutService).failOverdue();
    }

    @Test
    @DisplayName("실패해도 예외를 밖으로 던지지 않는다")
    void swallows_failures() {
        // given
        given(dispatchService.dispatch()).willThrow(new IllegalStateException("supplier down"));
        given(timeoutService.failOverdue()).willThrow(new IllegalStateException("db down"));

        // when // then
        assertThatCode(schedulers::dispatch).doesNotThrowAnyException();
        assertThatCode(schedulers::failOverdue).doesNotThrowAnyException();
    }
}
