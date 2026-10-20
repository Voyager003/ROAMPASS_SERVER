package com.roampass_server.plan.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.roampass_server.common.domain.exception.InvalidValueException;

@DisplayName("PlanSpec")
class PlanSpecTest {

    @Nested
    @DisplayName("올바른 값으로 만들면")
    class WhenValid {

        @Test
        @DisplayName("국가 코드·사용 일수·데이터 용량을 가진다")
        void keeps_values() {
            // when
            PlanSpec spec = PlanSpec.of("JP", 5, 3_072);

            // then
            assertThat(spec.countryCode()).isEqualTo("JP");
            assertThat(spec.days()).isEqualTo(5);
            assertThat(spec.dataMb()).isEqualTo(3_072);
        }
    }

    @Nested
    @DisplayName("잘못된 값으로 만들면 INVALID_PLAN으로 거부된다")
    class WhenInvalid {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"J", "JPN", "jp", "1A"})
        @DisplayName("국가 코드가 대문자 2글자(ISO 3166-1 alpha-2)가 아니면")
        void rejects_malformed_country_code(String countryCode) {
            // when & then
            assertThatThrownBy(() -> PlanSpec.of(countryCode, 5, 3_072))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PLAN");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("사용 일수가 1보다 작으면")
        void rejects_non_positive_days(int days) {
            // when & then
            assertThatThrownBy(() -> PlanSpec.of("JP", days, 3_072))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PLAN");
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("데이터 용량이 1MB보다 작으면")
        void rejects_non_positive_data(int dataMb) {
            // when & then
            assertThatThrownBy(() -> PlanSpec.of("JP", 5, dataMb))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PLAN");
        }
    }
}
