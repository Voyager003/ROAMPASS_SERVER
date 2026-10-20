package com.roampass_server.payment.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

@DisplayName("IdempotencyKey")
class IdempotencyKeyTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-7K3Q9XPA");

    @Test
    @DisplayName("주문번호와 결제 시도 번호로 만든다")
    void built_from_order_no_and_attempt() {
        // when
        IdempotencyKey key = IdempotencyKey.of(ORDER_NO, 2);

        // then
        assertThat(key.value()).isEqualTo("ORD-20261020-7K3Q9XPA-2");
    }

    @Test
    @DisplayName("시도 번호는 1부터다")
    void attempt_starts_at_one() {
        // when & then
        assertThatThrownBy(() -> IdempotencyKey.of(ORDER_NO, 0))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("저장된 문자열로 다시 만들 수 있다")
    void restored_from_string() {
        // when
        IdempotencyKey key = IdempotencyKey.from("ORD-20261020-7K3Q9XPA-12");

        // then
        assertThat(key).isEqualTo(IdempotencyKey.of(ORDER_NO, 12));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ORD-20261020-7K3Q9XPA", "ORD-20261020-7K3Q9XPA-0", "ORD-20261020-7K3Q9XPA-x", "abc"})
    @DisplayName("형식이 다른 문자열은 거부된다")
    void rejects_malformed_string(String raw) {
        // when & then
        assertThatThrownBy(() -> IdempotencyKey.from(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    @DisplayName("키에서 주문번호를 꺼낸다")
    void extracts_order_no() {
        // when // then
        assertThat(IdempotencyKey.from("ORD-20261020-7K3Q9XPA-12").orderNo())
                .isEqualTo(OrderNo.of("ORD-20261020-7K3Q9XPA"));
    }
}
