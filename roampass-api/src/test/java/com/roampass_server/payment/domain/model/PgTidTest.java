package com.roampass_server.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.InvalidValueException;

@DisplayName("PgTid")
class PgTidTest {

    @Test
    @DisplayName("PG가 준 거래 ID를 그대로 가진다")
    void keeps_value() {
        // when & then
        assertThat(PgTid.of("pg_20261020_0001").value()).isEqualTo("pg_20261020_0001");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "tid 1", "tid\n1"})
    @DisplayName("비어 있거나 공백·제어 문자가 있으면 INVALID_PG_TID로 거부된다")
    void rejects_blank_or_whitespace(String raw) {
        // when & then
        assertThatThrownBy(() -> PgTid.of(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_PG_TID");
    }

    @Test
    @DisplayName("100자를 넘으면 거부된다")
    void rejects_too_long() {
        // when & then
        assertThatThrownBy(() -> PgTid.of("t".repeat(101)))
                .isInstanceOf(InvalidValueException.class);
    }
}
