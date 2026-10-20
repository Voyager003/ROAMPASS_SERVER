package com.roampass_server.callback.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("통지 서명(HMAC-SHA256)")
class HmacSignatureTest {

    private final HmacSignature signature = new HmacSignature("Jefe");

    @Test
    @DisplayName("RFC 4231 테스트 케이스 2와 같은 값을 만든다")
    void matches_rfc4231_vector() {
        // when // then
        assertThat(signature.sign("what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    @DisplayName("본문이 한 글자라도 바뀌거나 서명이 없으면 검증 실패")
    void rejects_tampered_or_missing() {
        // given
        String body = "{\"amount\":9900}";
        String valid = signature.sign(body);

        // when // then
        assertThat(signature.verify(body, valid)).isTrue();
        assertThat(signature.verify(body, valid.toUpperCase())).isTrue();
        assertThat(signature.verify("{\"amount\":9901}", valid)).isFalse();
        assertThat(signature.verify(body, null)).isFalse();
        assertThat(new HmacSignature("other-secret").verify(body, valid)).isFalse();
    }
}
