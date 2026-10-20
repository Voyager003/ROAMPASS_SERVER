package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Function;

import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.callback.domain.service.HmacSignature;

import lombok.extern.slf4j.Slf4j;

/**
 * 공급사 콜백 수신 입구. 트랜잭션 없음
 * 서명(X-Signature) 불일치는 별도로 기록 후 거절, 검증 통과 후에만 본문 해석
 */
@Slf4j
public class IssuanceCallbackService {

    private final HmacSignature signature;
    private final CallbackLogRepository callbackLogRepository;
    private final IssuanceCallbackProcessor processor;
    private final Clock clock;

    public IssuanceCallbackService(HmacSignature signature, CallbackLogRepository callbackLogRepository,
                                   IssuanceCallbackProcessor processor, Clock clock) {
        this.signature = signature;
        this.callbackLogRepository = callbackLogRepository;
        this.processor = processor;
        this.clock = clock;
    }

    /** @throws InvalidSignatureException 서명 불일치(401) */
    public CallbackResult receive(String rawBody, String signatureHeader,
                                  Function<String, IssuanceCallbackCommand> parser) {
        if (!signature.verify(rawBody, signatureHeader)) {
            log.warn("Supplier callback signature mismatch");
            callbackLogRepository.save(CallbackLog.signatureRejected(CallbackSource.SUPPLIER, rawBody,
                    Instant.now(clock)));
            throw new InvalidSignatureException();
        }
        return processor.process(parser.apply(rawBody), rawBody);
    }
}
