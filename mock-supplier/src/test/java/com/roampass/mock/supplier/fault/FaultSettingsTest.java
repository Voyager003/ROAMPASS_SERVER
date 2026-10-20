package com.roampass.mock.supplier.fault;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.roampass.mock.supplier.issuance.MockSupplierException;

@DisplayName("장애 주입 설정")
class FaultSettingsTest {

    @Test
    @DisplayName("기본값(장애 없음)은 유효하다")
    void none_is_valid() {
        // when // then
        assertThatCode(FaultSettings::none).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "실패율 {0}, 지연 {1}ms, 중복 {2}")
    @CsvSource({"1.1, 0, 0", "-0.1, 0, 0", "0, -1, 0", "0, 60001, 0", "0, 0, -1", "0, 0, 11"})
    @DisplayName("범위를 벗어나면 400 INVALID_FAULT_CONFIG")
    void rejects_out_of_range(double failureRate, long delayMs, int duplicates) {
        // when // then
        assertThatThrownBy(() -> new FaultSettings(failureRate, delayMs, duplicates, false, false))
                .isInstanceOfSatisfying(MockSupplierException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_FAULT_CONFIG"));
    }
}
