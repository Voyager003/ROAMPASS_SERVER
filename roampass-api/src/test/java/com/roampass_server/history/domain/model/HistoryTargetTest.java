package com.roampass_server.history.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

@DisplayName("HistoryTarget")
class HistoryTargetTest {

    @Test
    @DisplayName("대상 종류와 그 대상의 키를 가진다")
    void keeps_type_and_key() {
        // when
        HistoryTarget target = HistoryTarget.of(TargetType.PAYMENT, "ORD-20261020-7K3Q9XPA-1");

        // then
        assertThat(target.type()).isEqualTo(TargetType.PAYMENT);
        assertThat(target.key()).isEqualTo("ORD-20261020-7K3Q9XPA-1");
    }

    @Test
    @DisplayName("주문 대상은 주문번호를 키로 쓴다")
    void order_target_uses_order_no() {
        // when
        HistoryTarget target = HistoryTarget.order(OrderNo.of("ORD-20261020-7K3Q9XPA"));

        // then
        assertThat(target).isEqualTo(HistoryTarget.of(TargetType.ORDER, "ORD-20261020-7K3Q9XPA"));
    }

    @Test
    @DisplayName("종류나 키가 없으면 INVALID_HISTORY로 거부된다")
    void rejects_missing() {
        // when & then
        assertThatThrownBy(() -> HistoryTarget.of(null, "k"))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_HISTORY");
        assertThatThrownBy(() -> HistoryTarget.of(TargetType.ORDER, " "))
                .isInstanceOf(InvalidValueException.class);
    }
}
