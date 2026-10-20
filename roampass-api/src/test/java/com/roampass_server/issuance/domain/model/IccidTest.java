package com.roampass_server.issuance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.InvalidValueException;

@DisplayName("Iccid")
class IccidTest {

    @ParameterizedTest
    @ValueSource(strings = {"8982300912345678902", "89820123456789012342"})
    @DisplayName("19~20자리 숫자이고 Luhn 체크 디지트가 맞으면 만들어진다")
    void accepts_valid_iccid(String raw) {
        // when & then
        assertThat(Iccid.of(raw).value()).isEqualTo(raw);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8982300912345678903", "89820123456789012343"})
    @DisplayName("체크 디지트가 틀리면 INVALID_ICCID로 거부된다")
    void rejects_wrong_check_digit(String raw) {
        // when & then
        assertThatThrownBy(() -> Iccid.of(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_ICCID");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"898230091234567890", "898201234567890123420", "8982300912345678a02", "8982 300912345678902"})
    @DisplayName("자릿수가 19~20이 아니거나 숫자가 아니면 거부된다")
    void rejects_wrong_length_or_non_digits(String raw) {
        // when & then
        assertThatThrownBy(() -> Iccid.of(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_ICCID");
    }
}
