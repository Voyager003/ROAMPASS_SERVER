package com.roampass_server.issuance.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.issuance.application.CallbackResult;
import com.roampass_server.issuance.application.IssuanceCallbackService;

import tools.jackson.databind.json.JsonMapper;

/**
 * 공급사 발급 콜백 수신. 인증 헤더 대신 본문 서명(X-Signature)으로 발신자 확인
 * 응답 규칙은 PG 웹훅과 같음(decisions #74): 진짜 공급사의 통지면 200 + result, 서명 불일치 401
 */
@RestController
public class IssuanceCallbackController {

    static final String SIGNATURE_HEADER = "X-Signature";

    private final IssuanceCallbackService callbackService;
    private final IssuanceCallbackParser parser;

    public IssuanceCallbackController(IssuanceCallbackService callbackService, JsonMapper jsonMapper) {
        this.callbackService = callbackService;
        this.parser = new IssuanceCallbackParser(jsonMapper);
    }

    @PostMapping("/api/issuance/callback")
    public CallbackResponse receive(@RequestBody String rawBody,
                                    @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        return new CallbackResponse(callbackService.receive(rawBody, signature, parser::parse));
    }

    public record CallbackResponse(CallbackResult result) {
    }
}
