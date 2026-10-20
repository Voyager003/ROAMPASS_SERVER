package com.roampass.mock.pg.payment;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.roampass.mock.pg.webhook.WebhookEvent;
import com.roampass.mock.pg.webhook.WebhookEventType;
import com.roampass.mock.pg.webhook.WebhookPublisher;

/**
 * 모의 PG의 승인·취소·상태 조회. 상태는 메모리 보관(재시작 시 초기화)
 * 멱등 키 선점은 putIfAbsent 한 번으로 판정, 동시 요청도 결제 1건·웹훅 1건
 */
@Service
public class MockPgService {

    private final Map<String, MockPayment> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, MockPayment> byPgTid = new ConcurrentHashMap<>();
    private final WebhookPublisher webhookPublisher;

    public MockPgService(WebhookPublisher webhookPublisher) {
        this.webhookPublisher = webhookPublisher;
    }

    public PaymentView approve(ApproveCommand command) {
        MockPayment created = new MockPayment(command, newId("pg_"), MockPaymentStatus.APPROVED, Instant.now());
        MockPayment existing = byIdempotencyKey.putIfAbsent(command.idempotencyKey(), created);
        if (existing != null) {
            if (!existing.isSameRequest(command)) {
                throw new MockPgException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency key already used with a different request");
            }
            return existing.view();
        }
        byPgTid.put(created.pgTid(), created);
        PaymentView view = created.view();
        webhookPublisher.publish(new WebhookEvent(newId("evt_"), WebhookEventType.PAYMENT_APPROVED,
                view.idempotencyKey(), view.pgTid(), view.orderNo(), view.amount(), view.currency(), view.approvedAt()));
        return view;
    }

    public CancelResult cancel(CancelCommand command) {
        MockPayment payment = byPgTid.get(command.pgTid());
        if (payment == null) {
            throw new MockPgException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Unknown PG transaction id");
        }
        return payment.cancel(command);
    }

    public Optional<PaymentView> find(String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey)).map(MockPayment::view);
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
