package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;

import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.callback.domain.service.HmacSignature;

import lombok.extern.slf4j.Slf4j;

/**
 * PG 웹훅 수신 입구. 트랜잭션 없음
 * 서명 불일치는 별도로 기록 후 거절(처리 트랜잭션과 분리해야 거절 기록이 롤백되지 않음)
 */
@Slf4j
public class PaymentWebhookService {

    private final HmacSignature signature;
    private final CallbackLogRepository callbackLogRepository;
    private final PaymentWebhookProcessor processor;
    private final Clock clock;

    public PaymentWebhookService(HmacSignature signature, CallbackLogRepository callbackLogRepository,
                                 PaymentWebhookProcessor processor, Clock clock) {
        this.signature = signature;
        this.callbackLogRepository = callbackLogRepository;
        this.processor = processor;
        this.clock = clock;
    }

    /**
     * @param rawBody         받은 본문 원문(서명 대상)
     * @param signatureHeader X-PG-Signature 헤더 값
     * @throws InvalidSignatureException 서명 불일치(401)
     */
    public WebhookResult receive(PaymentWebhookCommand command, String rawBody, String signatureHeader) {
        if (!signature.verify(rawBody, signatureHeader)) {
            log.warn("PG webhook signature mismatch");
            callbackLogRepository.save(CallbackLog.signatureRejected(CallbackSource.PG, rawBody, Instant.now(clock)));
            throw new InvalidSignatureException();
        }
        return processor.process(command, rawBody);
    }
}
