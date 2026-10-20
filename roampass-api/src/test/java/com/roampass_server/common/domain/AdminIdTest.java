package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("AdminId")
class AdminIdTest {

    @Nested
    @DisplayName("형식에 맞는 값으로 만들면")
    class WhenValueIsValid {

        @Test
        @DisplayName("그 값을 그대로 가진다")
        void keeps_the_value() {
            // when
            AdminId adminId = AdminId.of("admin-kim");

            // then
            assertThat(adminId.value()).isEqualTo("admin-kim");
        }
    }

    @Nested
    @DisplayName("형식에 맞지 않는 값으로 만들면")
    class WhenValueIsInvalid {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"admin kim", "admin;drop", "운영자"})
        @DisplayName("INVALID_ADMIN_ID로 거부된다")
        void rejects_with_invalid_admin_id_code(String raw) {
            // when & then
            assertThatThrownBy(() -> AdminId.of(raw))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_ADMIN_ID");
        }

        @Test
        @DisplayName("65자 이상이면 거부된다")
        void rejects_more_than_64_characters() {
            // when & then
            assertThatThrownBy(() -> AdminId.of("a".repeat(65)))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
