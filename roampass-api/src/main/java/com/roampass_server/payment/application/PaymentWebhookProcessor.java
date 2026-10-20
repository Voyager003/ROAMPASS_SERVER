package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.PgTid;

import lombok.extern.slf4j.Slf4j;

/**
 * 서명 검증을 통과한 PG 웹훅 처리(한 트랜잭션)
 * 수신 기록 선 INSERT(event_key 유니크)로 중복 판정 → 결제 확정 → 처리 결과 갱신
 * 동시에 같은 이벤트가 오면 나중 INSERT는 앞 트랜잭션 커밋까지 대기 후 중복으로 판정
 */
@Slf4j
public class PaymentWebhookProcessor {

    static final String APPROVED = "PAYMENT_APPROVED";
    static final String FAILED = "PAYMENT_FAILED";

    private final CallbackLogRepository callbackLogRepository;
    private final PaymentConfirmationService confirmationService;
    private final Clock clock;

    public PaymentWebhookProcessor(CallbackLogRepository callbackLogRepository,
                                   PaymentConfirmationService confirmationService, Clock clock) {
        this.callbackLogRepository = callbackLogRepository;
        this.confirmationService = confirmationService;
        this.clock = clock;
    }

    public static String eventKey(String eventId) {
        return "PG:" + eventId;
    }

    @Transactional
    public WebhookResult process(PaymentWebhookCommand command, String rawBody) {
        if (command.eventId() == null || command.eventId().isBlank()) {
            throw new InvalidValueException("INVALID_WEBHOOK", "eventId is required");
        }
        String eventKey = eventKey(command.eventId());
        IdempotencyKey key = IdempotencyKey.from(command.idempotencyKey());
        if (!callbackLogRepository.insertIfAbsent(
                CallbackLog.received(CallbackSource.PG, eventKey, key.orderNo(), rawBody, Instant.now(clock)))) {
            log.info("Duplicate PG webhook {}", eventKey);
            return WebhookResult.DUPLICATE;
        }
        ConfirmationResult result = confirm(command);
        WebhookResult webhookResult = toWebhookResult(result);
        callbackLogRepository.updateOutcome(eventKey, toOutcome(webhookResult), result.name());
        return webhookResult;
    }

    private ConfirmationResult confirm(PaymentWebhookCommand command) {
        IdempotencyKey key = IdempotencyKey.from(command.idempotencyKey());
        return switch (command.eventType() == null ? "" : command.eventType()) {
            case APPROVED -> confirmationService.confirmApproval(key, PgTid.of(command.pgTid()),
                    Money.of(command.amount(), currency(command.currency())), Actor.pg());
            case FAILED -> confirmationService.confirmFailure(key, "PG 실패 통지", Actor.pg());
            default -> {
                log.warn("Unknown PG webhook type {}", command.eventType());
                yield ConfirmationResult.IGNORED;
            }
        };
    }

    private static Currency currency(String value) {
        try {
            return Currency.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidValueException("INVALID_WEBHOOK", "Unsupported currency: " + value);
        }
    }

    private static WebhookResult toWebhookResult(ConfirmationResult result) {
        return switch (result) {
            case CONFIRMED, LATE_APPROVAL_REFUNDING, FAILED -> WebhookResult.PROCESSED;
            case ALREADY_APPLIED -> WebhookResult.ALREADY_APPLIED;
            case AMOUNT_MISMATCH -> WebhookResult.AMOUNT_MISMATCH;
            case PAYMENT_NOT_FOUND -> WebhookResult.PAYMENT_NOT_FOUND;
            case IGNORED -> WebhookResult.IGNORED;
        };
    }

    private static CallbackOutcome toOutcome(WebhookResult result) {
        return switch (result) {
            case PROCESSED -> CallbackOutcome.PROCESSED;
            case ALREADY_APPLIED -> CallbackOutcome.ALREADY_APPLIED;
            case AMOUNT_MISMATCH -> CallbackOutcome.AMOUNT_MISMATCH;
            case PAYMENT_NOT_FOUND -> CallbackOutcome.TARGET_NOT_FOUND;
            case IGNORED, DUPLICATE -> CallbackOutcome.IGNORED;
        };
    }
}
