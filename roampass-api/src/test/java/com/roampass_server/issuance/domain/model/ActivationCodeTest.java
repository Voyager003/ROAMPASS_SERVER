package com.roampass_server.issuance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.InvalidValueException;

@DisplayName("ActivationCode")
class ActivationCodeTest {

    @Test
    @DisplayName("LPA:1${SM-DP+ 주소}${매칭 ID} 형식이면 만들어진다 (부록 A 예시)")
    void accepts_lpa_format() {
        // when & then
        assertThat(ActivationCode.of("LPA:1$smdp.example$X1Y2").value()).isEqualTo("LPA:1$smdp.example$X1Y2");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"smdp.example$X1Y2", "LPA:1$smdp.example", "LPA:1$$X1Y2", "LPA:1$smdp.example$", "LPA:1$smdp example$X1Y2"})
    @DisplayName("형식이 다르면 INVALID_ACTIVATION_CODE로 거부된다")
    void rejects_malformed(String raw) {
        // when & then
        assertThatThrownBy(() -> ActivationCode.of(raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code").isEqualTo("INVALID_ACTIVATION_CODE");
    }
}
