package com.roampass_server.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PaymentStatus 전이 규칙")
class PaymentStatusTest {

    @ParameterizedTest(name = "{0} → {1} 허용")
    @CsvSource({
            "REQUESTED, APPROVED", "REQUESTED, FAILED",
            "APPROVED, PARTIALLY_CANCELLED", "APPROVED, CANCELLED",
            "PARTIALLY_CANCELLED, PARTIALLY_CANCELLED", "PARTIALLY_CANCELLED, CANCELLED"
    })
    @DisplayName("허용된 전이")
    void allowed(PaymentStatus from, PaymentStatus to) {
        // when & then
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} → {1} 거부")
    @CsvSource({
            "FAILED, APPROVED",       // a final result is never flipped
            "APPROVED, FAILED",
            "CANCELLED, APPROVED",
            "REQUESTED, CANCELLED",   // nothing to cancel yet
            "APPROVED, REQUESTED"
    })
    @DisplayName("금지된 전이")
    void forbidden(PaymentStatus from, PaymentStatus to) {
        // when & then
        assertThat(from.canTransitionTo(to)).isFalse();
    }
}
