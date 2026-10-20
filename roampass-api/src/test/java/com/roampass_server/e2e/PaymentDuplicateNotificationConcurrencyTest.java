package com.roampass_server.e2e;

import static com.roampass_server.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.ConfirmationResult;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.application.PaymentWebhookCommand;
import com.roampass_server.payment.application.PaymentWebhookService;
import com.roampass_server.payment.application.WebhookResult;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.support.IntegrationTest;

/**
 * 과제 필수 테스트: 같은 결제 통지 N개를 동시에 보내도 결제는 한 번만 처리
 * 실제 PostgreSQL, 스레드마다 별도 트랜잭션 커밋(테스트 트랜잭션 롤백 없음)
 * 주문번호가 매번 새로 생성되어 테스트끼리 데이터가 섞이지 않음
 */
@IntegrationTest
@DisplayName("동시성: 중복 결제 통지")
class PaymentDuplicateNotificationConcurrencyTest {

    private static final int THREADS = 16;
    private static final UserId USER = UserId.of("concurrency-user");

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private PaymentWebhookService webhookService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${roampass.pg.webhook-secret}")
    private String webhookSecret;

    private Payment requestedPayment() {
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        OrderNo orderNo = orderService.createOrder(USER, 1L, travelDate).orderNo();
        return paymentRequestService.start(USER, orderNo);
    }

    private String webhookBody(Payment payment, String eventId) {
        return """
                {"eventId":"%s","eventType":"PAYMENT_APPROVED","idempotencyKey":"%s","pgTid":"pg_concurrency",
                 "amount":%d,"currency":"KRW"}
                """.formatted(eventId, payment.idempotencyKey().value(), payment.amount().amount());
    }

    private WebhookResult sendWebhook(Payment payment, String eventId) {
        String body = webhookBody(payment, eventId);
        String signature = new HmacSignature(webhookSecret).sign(body);
        PaymentWebhookCommand command = new PaymentWebhookCommand(eventId, "PAYMENT_APPROVED",
                payment.idempotencyKey().value(), "pg_concurrency", payment.amount().amount(), "KRW");
        return webhookService.receive(body, signature, raw -> command);
    }

    private void assertProcessedExactlyOnce(Payment payment) {
        Payment stored = paymentRepository.findByIdempotencyKey(payment.idempotencyKey()).orElseThrow();
        assertThat(stored.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(orderRepository.findByOrderNo(payment.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.PAID);
        List<StatusHistory> histories = historyRepository.findByOrderNo(payment.orderNo());
        assertThat(histories)
                .filteredOn(h -> h.target().type() == TargetType.PAYMENT && "APPROVED".equals(h.toStatus()))
                .hasSize(1);
        assertThat(histories)
                .filteredOn(h -> h.target().type() == TargetType.ORDER && "PAID".equals(h.toStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("같은 승인 웹훅(같은 eventId) 16개 동시 → 1개 PROCESSED·15개 DUPLICATE, 결제 APPROVED·주문 PAID·이력 각 1건, 수신 기록 1건")
    void same_webhook_sent_concurrently() throws Exception {
        // given
        Payment payment = requestedPayment();
        String eventId = "evt_same_" + payment.idempotencyKey().value();
        List<Callable<WebhookResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> sendWebhook(payment, eventId));
        }

        // when
        List<WebhookResult> results = runConcurrently(tasks);

        // then
        Map<WebhookResult, Long> counts = results.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(counts).containsEntry(WebhookResult.PROCESSED, 1L)
                .containsEntry(WebhookResult.DUPLICATE, (long) THREADS - 1);
        assertProcessedExactlyOnce(payment);
        assertThat(jdbcTemplate.queryForObject("select count(*) from callback_log where event_key = ?",
                Integer.class, "PG:" + eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("승인 응답 경로 8개 + 서로 다른 eventId 웹훅 8개 동시 → 반영 1개·나머지 ALREADY_APPLIED, 결제·주문 이력 각 1건")
    void response_and_distinct_webhooks_concurrently() throws Exception {
        // given
        Payment payment = requestedPayment();
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            tasks.add(() -> confirmationService.confirmApproval(payment.idempotencyKey(),
                    PgTid.of("pg_concurrency"), payment.amount(), Actor.pg()).name());
            String eventId = "evt_" + i + "_" + payment.idempotencyKey().value();
            tasks.add(() -> sendWebhook(payment, eventId).name());
        }

        // when
        List<String> results = runConcurrently(tasks);

        // then
        long applied = results.stream()
                .filter(r -> r.equals(ConfirmationResult.CONFIRMED.name()) || r.equals(WebhookResult.PROCESSED.name()))
                .count();
        long alreadyApplied = results.stream()
                .filter(r -> r.equals(ConfirmationResult.ALREADY_APPLIED.name()))
                .count();
        assertThat(applied).isEqualTo(1);
        assertThat(alreadyApplied).isEqualTo(THREADS - 1);
        assertProcessedExactlyOnce(payment);
    }
}
