package com.roampass_server.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.exception.InvalidValueException;

class OrderIdempotencyKeyTest {

    @ParameterizedTest(name = "\"{0}\" 허용")
    @ValueSource(strings = {"3f6c1a2e-7b7d-4c55-9f0e-1d2b3c4d5e6f", "a", "key_1-A"})
    void accepts_letters_digits_dash_underscore(String value) {
        // when // then
        assertThat(OrderIdempotencyKey.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest(name = "\"{0}\" 거절")
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "한글키", "a/b"})
    void rejects_invalid_format(String value) {
        // when // then
        assertThatThrownBy(() -> OrderIdempotencyKey.of(value))
                .isInstanceOf(InvalidValueException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_IDEMPOTENCY_KEY");
    }

    @org.junit.jupiter.api.Test
    void rejects_longer_than_64() {
        // when // then
        assertThatThrownBy(() -> OrderIdempotencyKey.of("k".repeat(65)))
                .isInstanceOf(InvalidValueException.class);
    }
}
