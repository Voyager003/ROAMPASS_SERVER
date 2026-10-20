package com.roampass.mock.pg.payment;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.roampass.mock.pg.fault.FaultConfig;
import com.roampass.mock.pg.fault.FaultSettings;
import com.roampass.mock.pg.webhook.WebhookEvent;
import com.roampass.mock.pg.webhook.WebhookEventType;
import com.roampass.mock.pg.webhook.WebhookPublisher;

/**
 * 모의 PG의 승인·취소·상태 조회. 상태는 메모리 보관(재시작 시 초기화)
 * 멱등 키 선점은 putIfAbsent 한 번으로 판정, 동시 요청도 결제 1건·웹훅 1건
 * 장애 주입({@link FaultConfig})으로 실패·지연·웹훅 중복/생략/금액 변조·취소 실패 재현
 */
@Service
public class MockPgService {

    private final Map<String, MockPayment> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, MockPayment> byPgTid = new ConcurrentHashMap<>();
    private final WebhookPublisher webhookPublisher;
    private final FaultConfig faultConfig;

    public MockPgService(WebhookPublisher webhookPublisher, FaultConfig faultConfig) {
        this.webhookPublisher = webhookPublisher;
        this.faultConfig = faultConfig;
    }

    public PaymentView approve(ApproveCommand command) {
        FaultSettings faults = faultConfig.current();
        MockPayment created = faultConfig.roll(faults.approveFailureRate())
                ? new MockPayment(command, null, MockPaymentStatus.FAILED, null)
                : new MockPayment(command, newId("pg_"), MockPaymentStatus.APPROVED, Instant.now());
        MockPayment existing = byIdempotencyKey.putIfAbsent(command.idempotencyKey(), created);
        if (existing != null) {
            if (!existing.isSameRequest(command)) {
                throw new MockPgException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key already used with a different request");
            }
            return existing.view();
        }
        if (created.pgTid() != null) {
            byPgTid.put(created.pgTid(), created);
        }
        PaymentView view = created.view();
        publishWebhooks(view, faults);
        delay(faults.responseDelayMs()); // 결제 기록·웹훅 예약 뒤 지연, 응답만 늦어지는 상황
        return view;
    }

    public CancelResult cancel(CancelCommand command) {
        MockPayment payment = byPgTid.get(command.pgTid());
        if (payment == null) {
            throw new MockPgException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Unknown PG transaction id");
        }
        if (faultConfig.roll(faultConfig.current().cancelFailureRate())) {
            throw new MockPgException(HttpStatus.INTERNAL_SERVER_ERROR, "CANCEL_FAILED", "Injected cancel failure");
        }
        return payment.cancel(command);
    }

    public Optional<PaymentView> find(String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey)).map(MockPayment::view);
    }

    private void publishWebhooks(PaymentView view, FaultSettings faults) {
        if (faults.webhookDrop()) {
            return;
        }
        WebhookEventType type = view.status() == MockPaymentStatus.APPROVED
                ? WebhookEventType.PAYMENT_APPROVED : WebhookEventType.PAYMENT_FAILED;
        WebhookEvent event = new WebhookEvent(newId("evt_"), type, view.idempotencyKey(), view.pgTid(),
                view.orderNo(), view.amount() + faults.webhookAmountDelta(), view.currency(), Instant.now());
        for (int i = 0; i <= faults.webhookDuplicates(); i++) {
            webhookPublisher.publish(event); // 중복분도 같은 eventId
        }
    }

    private static void delay(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
