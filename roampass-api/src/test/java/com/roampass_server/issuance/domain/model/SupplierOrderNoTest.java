package com.roampass_server.issuance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.exception.InvalidValueException;

@DisplayName("SupplierOrderNo")
class SupplierOrderNoTest {

    @Test
    @DisplayName("공급사가 준 접수 번호를 그대로 가진다")
    void keeps_value() {
        // when & then
        assertThat(SupplierOrderNo.of("SUP-000123").value()).isEqualTo("SUP-000123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "SUP 1"})
    @DisplayName("비어 있거나 공백이 있으면 INVALID_SUPPLIER_ORDER_NO로 거부된다")
    void rejects_blank(String raw) {
        // when & then
        assertThatThrownBy(() -> SupplierOrderNo.of(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_SUPPLIER_ORDER_NO");
    }
}
