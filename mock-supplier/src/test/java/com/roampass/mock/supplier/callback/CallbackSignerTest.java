package com.roampass.mock.supplier.callback;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("콜백 서명")
class CallbackSignerTest {

    @Test
    @DisplayName("HMAC-SHA256 결과를 소문자 16진수로 돌려준다 (RFC 4231 테스트 케이스 2)")
    void matches_rfc4231_vector() {
        // when // then
        assertThat(new CallbackSigner("Jefe").sign("what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }
}
