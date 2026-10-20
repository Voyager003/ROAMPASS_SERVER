package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IllegalStateTransitionException")
class IllegalStateTransitionExceptionTest {

    @Test
    @DisplayName("대상·현재 상태·목표 상태를 메시지에 담고 ILLEGAL_STATE_TRANSITION 코드를 가진다")
    void describes_subject_and_states() {
        // when
        var exception = new IllegalStateTransitionException("Order", "PENDING", "ISSUED");

        // then
        assertThat(exception)
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Order")
                .hasMessageContaining("PENDING")
                .hasMessageContaining("ISSUED");
        assertThat(exception.getCode()).isEqualTo("ILLEGAL_STATE_TRANSITION");
    }
}
