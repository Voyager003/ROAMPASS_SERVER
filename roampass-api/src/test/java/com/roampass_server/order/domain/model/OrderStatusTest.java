package com.roampass_server.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("OrderStatus 전이 규칙")
class OrderStatusTest {

    @ParameterizedTest(name = "{0} → {1} 허용")
    @CsvSource({
            "PENDING, PAID", "PENDING, PICKUP_READY", "PENDING, EXPIRED",
            "EXPIRED, REFUNDING",
            "PAID, ISSUE_REQUESTED", "PAID, REFUNDING",
            "PICKUP_READY, ISSUED", "PICKUP_READY, REFUNDING",
            "ISSUE_REQUESTED, ISSUED", "ISSUE_REQUESTED, ISSUE_FAILED",
            "ISSUED, REFUNDING",
            "ISSUE_FAILED, REFUNDING",
            "REFUNDING, REFUNDED", "REFUNDING, NEEDS_ATTENTION",
            "NEEDS_ATTENTION, REFUNDING", "NEEDS_ATTENTION, ISSUE_REQUESTED"
    })
    @DisplayName("허용된 전이")
    void allowed_transitions(OrderStatus from, OrderStatus to) {
        // when & then
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 거부")
    @CsvSource({
            "EXPIRED, PAID",            // an expired order is never revived
            "PAID, PENDING",            // no going back
            "ISSUED, ISSUE_REQUESTED",  // never issue twice
            "ISSUE_REQUESTED, REFUNDING", // must fail (or succeed) first
            "PENDING, REFUNDING",       // nothing was paid yet
            "ISSUED, REFUNDED"          // refunds always go through REFUNDING
    })
    @DisplayName("대표적인 금지 전이")
    void forbidden_transitions(OrderStatus from, OrderStatus to) {
        // when & then
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    @DisplayName("REFUNDED는 종료 상태라 어디로도 가지 않는다")
    void refunded_is_terminal() {
        // when & then
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(OrderStatus.REFUNDED.canTransitionTo(to)).isFalse();
        }
    }

    @Test
    @DisplayName("자기 자신으로의 전이는 허용하지 않는다")
    void no_self_transition() {
        // when & then
        for (OrderStatus status : EnumSet.allOf(OrderStatus.class)) {
            assertThat(status.canTransitionTo(status)).isFalse();
        }
    }
}
