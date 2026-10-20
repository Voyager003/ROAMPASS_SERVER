package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("UserId")
class UserIdTest {

    @Nested
    @DisplayName("형식에 맞는 값으로 만들면")
    class WhenValueIsValid {

        @ParameterizedTest
        @ValueSource(strings = {"user-1", "USER_2", "a", "0123456789"})
        @DisplayName("그 값을 그대로 가진다")
        void keeps_the_value(String raw) {
            // when
            UserId userId = UserId.of(raw);

            // then
            assertThat(userId.value()).isEqualTo(raw);
        }

        @Test
        @DisplayName("64자까지는 허용된다")
        void accepts_64_characters() {
            // given
            String raw = "a".repeat(64);

            // when
            UserId userId = UserId.of(raw);

            // then
            assertThat(userId.value()).hasSize(64);
        }

        @Test
        @DisplayName("값이 같으면 같은 UserId다")
        void equal_when_values_are_equal() {
            // when & then
            assertThat(UserId.of("user-1")).isEqualTo(UserId.of("user-1"));
        }
    }

    @Nested
    @DisplayName("형식에 맞지 않는 값으로 만들면")
    class WhenValueIsInvalid {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("비어 있으면 INVALID_USER_ID로 거부된다")
        void rejects_null_or_empty(String raw) {
            // when & then
            assertThatThrownBy(() -> UserId.of(raw))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_USER_ID");
        }

        @Test
        @DisplayName("65자 이상이면 거부된다")
        void rejects_more_than_64_characters() {
            // given
            String raw = "a".repeat(65);

            // when & then
            assertThatThrownBy(() -> UserId.of(raw))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {" ", "user 1", "user\n1", "<script>", "사용자", "user.1"})
        @DisplayName("영문·숫자·_·- 외의 문자가 있으면 거부된다")
        void rejects_disallowed_characters(String raw) {
            // when & then
            assertThatThrownBy(() -> UserId.of(raw))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("에러 메시지에 입력값을 그대로 담지 않는다 (로그 주입 방지)")
        void error_message_does_not_echo_input() {
            // given
            String raw = "evil\nINJECTED";

            // when & then
            assertThatThrownBy(() -> UserId.of(raw))
                    .hasMessageNotContaining("INJECTED");
        }
    }
}
