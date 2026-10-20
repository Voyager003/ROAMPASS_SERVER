package com.roampass_server.issuance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.issuance.application.CallbackResult;
import com.roampass_server.issuance.application.IssuanceCallbackService;

@WebMvcTest(controllers = IssuanceCallbackController.class)
@DisplayName("POST /api/issuance/callback")
class IssuanceCallbackControllerTest {

    private static final String BODY = """
            {"eventId":"sevt_1","eventType":"ISSUED","supplierOrderNo":"SUP-1","orderNo":"ORD-20261021-AAAA1111",
             "iccid":"8982300912345678902","activationCode":"LPA:1$smdp.example$X1Y2","reason":null,
             "occurredAt":"2026-10-21T00:00:03Z"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private IssuanceCallbackService callbackService;

    @Test
    @DisplayName("본문 원문과 X-Signature를 넘기고 처리 결과를 200으로 돌려준다(인증 헤더 불필요)")
    void receives() {
        // given
        given(callbackService.receive(eq(BODY), eq("sig"), any())).willReturn(CallbackResult.PROCESSED);

        // when
        var result = mvc.post().uri("/api/issuance/callback").header("X-Signature", "sig")
                .contentType("application/json").content(BODY);

        // then
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.result").isEqualTo("PROCESSED");
    }

    @Test
    @DisplayName("서명이 틀리면 401 INVALID_SIGNATURE")
    void invalid_signature() {
        // given
        given(callbackService.receive(any(), any(), any())).willThrow(new InvalidSignatureException());

        // when
        var result = mvc.post().uri("/api/issuance/callback").header("X-Signature", "bad")
                .contentType("application/json").content(BODY);

        // then
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_SIGNATURE");
    }
}
