package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roampass_server.common.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Actor")
class ActorTest {

    @Nested
    @DisplayName("문자열로 표현하면")
    class AsString {

        @Test
        @DisplayName("사용자·운영자는 식별자를 포함하고, 시스템·PG·공급사는 종류만 쓴다")
        void formats() {
            // when & then
            assertThat(Actor.user(UserId.of("user-1")).asString()).isEqualTo("USER:user-1");
            assertThat(Actor.admin(AdminId.of("admin-kim")).asString()).isEqualTo("ADMIN:admin-kim");
            assertThat(Actor.system().asString()).isEqualTo("SYSTEM");
            assertThat(Actor.pg().asString()).isEqualTo("PG");
            assertThat(Actor.supplier().asString()).isEqualTo("SUPPLIER");
        }
    }

    @Nested
    @DisplayName("저장된 문자열에서 다시 만들면")
    class Parse {

        @ParameterizedTest
        @ValueSource(strings = {"USER:user-1", "ADMIN:admin-kim", "SYSTEM", "PG", "SUPPLIER"})
        @DisplayName("같은 Actor가 된다")
        void round_trips(String raw) {
            // when & then
            assertThat(Actor.parse(raw).asString()).isEqualTo(raw);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"USER", "ADMIN:", "SYSTEM:x", "ROBOT", "USER:bad id"})
        @DisplayName("형식이 틀리면 INVALID_ACTOR로 거부된다")
        void rejects_malformed(String raw) {
            // when & then
            assertThatThrownBy(() -> Actor.parse(raw))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Test
    @DisplayName("운영자인지 알 수 있다")
    void knows_admin() {
        // when & then
        assertThat(Actor.admin(AdminId.of("admin-kim")).isAdmin()).isTrue();
        assertThat(Actor.system().isAdmin()).isFalse();
    }
}
