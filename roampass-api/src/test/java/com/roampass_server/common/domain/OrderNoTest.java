package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roampass_server.common.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;


@DisplayName("OrderNo")
class OrderNoTest {

    @Test
    @DisplayName("ORD-{yyyyMMdd}-{영문 대문자·숫자 8자} 형식이면 만들어진다")
    void accepts_valid_format() {
        // when
        OrderNo orderNo = OrderNo.of("ORD-20261020-7K3Q9XPA");

        // then
        assertThat(orderNo.value()).isEqualTo("ORD-20261020-7K3Q9XPA");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ORD-2026102-7K3Q9XPA", "ORD-20261020-7K3Q9XP", "ord-20261020-7K3Q9XPA",
            "ORD-20261020-7k3q9xpa", "ORDER-20261020-7K3Q9XPA", "ORD-20261020-7K3Q9XPA "})
    @DisplayName("형식이 다르면 INVALID_ORDER_NO로 거부된다")
    void rejects_invalid_format(String raw) {
        // when & then
        assertThatThrownBy(() -> OrderNo.of(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_ORDER_NO");
    }
}
