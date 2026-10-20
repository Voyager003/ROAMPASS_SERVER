package com.roampass_server.order.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.order.application.OrderExpirationService;

@DisplayName("결제 대기 만료 스케줄러")
class OrderExpirationSchedulerTest {

    private final OrderExpirationService service = mock(OrderExpirationService.class);
    private final OrderExpirationScheduler scheduler = new OrderExpirationScheduler(service);

    @Test
    @DisplayName("실행되면 만료 유스케이스를 한 번 호출한다")
    void delegates_to_service() {
        // given
        given(service.expireDue()).willReturn(3);

        // when
        scheduler.expireDueOrders();

        // then
        verify(service).expireDue();
    }

    @Test
    @DisplayName("유스케이스가 실패해도 예외를 밖으로 던지지 않는다 (다음 주기에 재시도)")
    void swallows_failure() {
        // given
        given(service.expireDue()).willThrow(new IllegalStateException("db down"));

        // when // then
        assertThatCode(scheduler::expireDueOrders).doesNotThrowAnyException();
    }
}
