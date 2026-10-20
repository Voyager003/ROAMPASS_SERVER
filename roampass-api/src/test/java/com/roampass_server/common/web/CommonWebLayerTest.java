package com.roampass_server.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(controllers = WebLayerTestController.class)
@DisplayName("공통 웹 계층 (인증 헤더 · 에러 응답)")
class CommonWebLayerTest {

    @Autowired
    private MockMvcTester mvc;

    @Nested
    @DisplayName("X-User-Id 헤더는")
    class UserIdHeader {

        @Test
        @DisplayName("UserId 파라미터가 있는 API에 값이 있으면 그 값이 주입된다")
        void injects_user_id_when_present() {
            // when
            var result = mvc.get().uri("/api/test/me").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatusOk().hasBodyTextEqualTo("user-1");
        }

        @Test
        @DisplayName("UserId 파라미터가 있는 API에 없으면 401 MISSING_USER_ID")
        void returns_401_when_missing() {
            // when
            var result = mvc.get().uri("/api/test/me");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().extractingPath("$.code").isEqualTo("MISSING_USER_ID");
        }

        @Test
        @DisplayName("형식이 틀리면 400 INVALID_USER_ID")
        void returns_400_when_malformed() {
            // when
            var result = mvc.get().uri("/api/test/me").header("X-User-Id", "user 1");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_USER_ID");
        }

        @Test
        @DisplayName("UserId 파라미터가 없는 API는 헤더 없이도 호출된다")
        void not_required_for_endpoints_without_parameter() {
            // when
            var result = mvc.get().uri("/api/test/public");

            // then
            assertThat(result).hasStatusOk();
        }
    }

    @Nested
    @DisplayName("X-Admin-Id 헤더는")
    class AdminIdHeader {

        @Test
        @DisplayName("값이 있으면 AdminId로 주입된다")
        void injects_admin_id_when_present() {
            // when
            var result = mvc.get().uri("/admin/test/me").header("X-Admin-Id", "admin-kim");

            // then
            assertThat(result).hasStatusOk().hasBodyTextEqualTo("admin-kim");
        }

        @Test
        @DisplayName("/admin/** 에서는 AdminId 파라미터가 없어도 필수다 (401 MISSING_ADMIN_ID)")
        void required_for_every_admin_path() {
            // when
            var result = mvc.get().uri("/admin/test/no-param");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("MISSING_ADMIN_ID");
        }

        @Test
        @DisplayName("/admin/** 에서 형식이 틀리면 400 INVALID_ADMIN_ID")
        void returns_400_when_malformed() {
            // when
            var result = mvc.get().uri("/admin/test/no-param").header("X-Admin-Id", "admin;drop");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_ADMIN_ID");
        }

        @Test
        @DisplayName("X-User-Id만 있으면 어드민 API를 호출할 수 없다")
        void user_header_does_not_grant_admin_access() {
            // when
            var result = mvc.get().uri("/admin/test/no-param").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("예외가 나면")
    class WhenExceptionIsThrown {

        @Test
        @DisplayName("InvalidValueException은 400과 예외의 code로 응답한다")
        void invalid_value_maps_to_400() {
            // when
            var result = mvc.get().uri("/api/test/invalid-value");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SAMPLE");
        }

        @Test
        @DisplayName("IllegalStateTransitionException은 409 ILLEGAL_STATE_TRANSITION")
        void illegal_transition_maps_to_409() {
            // when
            var result = mvc.get().uri("/api/test/illegal-transition");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("ILLEGAL_STATE_TRANSITION");
        }

        @Test
        @DisplayName("예상하지 못한 예외는 500이며 내부 메시지를 노출하지 않는다")
        void unexpected_exception_maps_to_500_without_leaking() {
            // when
            var result = mvc.get().uri("/api/test/unexpected");

            // then
            assertThat(result)
                    .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyJson().extractingPath("$.detail").isEqualTo("Internal server error");
        }

        @Test
        @DisplayName("없는 경로는 500이 아니라 404 NOT_FOUND로 응답한다 (Spring 기본 예외에도 code 부여)")
        void unknown_path_is_still_404() {
            // when
            var result = mvc.get().uri("/api/does-not-exist");

            // then
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
        }
    }
}
