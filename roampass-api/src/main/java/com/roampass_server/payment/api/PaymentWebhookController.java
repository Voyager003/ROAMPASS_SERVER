package com.roampass_server.payment.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.payment.application.PaymentWebhookService;
import com.roampass_server.payment.application.WebhookResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * PG 웹훅 수신. 인증 헤더 대신 본문 서명(X-PG-Signature)으로 발신자 확인
 * 본문은 원문 문자열로 받아 서명 검증에 그대로 사용(역직렬화 후 다시 직렬화하면 바이트가 달라짐)
 * 수신 확인은 200, 처리 결과(중복·금액 불일치 등)는 본문의 result
 * (4xx로 응답하면 PG가 같은 통지를 재전송하는데, 금액 불일치는 재전송해도 결과가 같음)
 */
@RestController
public class PaymentWebhookController {

    static final String SIGNATURE_HEADER = "X-PG-Signature";

    private final PaymentWebhookService webhookService;
    private final PaymentWebhookParser parser;

    public PaymentWebhookController(PaymentWebhookService webhookService, JsonMapper jsonMapper) {
        this.webhookService = webhookService;
        this.parser = new PaymentWebhookParser(jsonMapper);
    }

    @PostMapping("/api/payments/webhook")
    public WebhookResponse receive(@RequestBody String rawBody,
                                   @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature) {
        return new WebhookResponse(webhookService.receive(rawBody, signature, parser::parse));
    }

    public record WebhookResponse(WebhookResult result) {
    }
}
