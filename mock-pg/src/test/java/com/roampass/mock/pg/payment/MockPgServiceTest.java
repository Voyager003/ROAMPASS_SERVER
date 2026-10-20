package com.roampass.mock.pg.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass.mock.pg.webhook.WebhookEvent;
import com.roampass.mock.pg.webhook.WebhookEventType;
import com.roampass.mock.pg.webhook.WebhookPublisher;

@DisplayName("mock-pg 결제 처리")
class MockPgServiceTest {

    private static final ApproveCommand REQUEST = new ApproveCommand("ORD-20261020-AAAA1111-1",
            "ORD-20261020-AAAA1111", 9_900, "KRW");

    private final List<WebhookEvent> published = new CopyOnWriteArrayList<>();
    private final WebhookPublisher publisher = published::add;
    private final MockPgService service = new MockPgService(publisher);

    @Nested
    @DisplayName("승인 요청이 오면")
    class Approve {

        @Test
        @DisplayName("승인하고 PG 거래 ID를 부여하며 승인 웹훅을 1건 발행한다")
        void approves_and_publishes_webhook() {
            // when
            PaymentView view = service.approve(REQUEST);

            // then
            assertThat(view.status()).isEqualTo(MockPaymentStatus.APPROVED);
            assertThat(view.pgTid()).startsWith("pg_");
            assertThat(published).singleElement().satisfies(event -> {
                assertThat(event.eventId()).startsWith("evt_");
                assertThat(event.eventType()).isEqualTo(WebhookEventType.PAYMENT_APPROVED);
                assertThat(event.pgTid()).isEqualTo(view.pgTid());
                assertThat(event.idempotencyKey()).isEqualTo(REQUEST.idempotencyKey());
                assertThat(event.amount()).isEqualTo(9_900);
            });
        }

        @Test
        @DisplayName("같은 멱등 키로 같은 요청이 다시 오면 처음 결과를 그대로 돌려주고 웹훅을 더 보내지 않는다")
        void replays_same_key() {
            // given
            PaymentView first = service.approve(REQUEST);

            // when
            PaymentView second = service.approve(REQUEST);

            // then
            assertThat(second.pgTid()).isEqualTo(first.pgTid());
            assertThat(published).hasSize(1);
        }

        @Test
        @DisplayName("같은 멱등 키로 금액이 다른 요청이 오면 409 IDEMPOTENCY_KEY_REUSED")
        void rejects_reused_key_with_different_request() {
            // given
            service.approve(REQUEST);

            // when // then
            assertThatThrownBy(() -> service.approve(new ApproveCommand(REQUEST.idempotencyKey(),
                    REQUEST.orderNo(), 1_000, "KRW")))
                    .isInstanceOfSatisfying(MockPgException.class,
                            e -> assertThat(e.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
        }

        @Test
        @DisplayName("같은 멱등 키 요청 20개가 동시에 와도 결제 1건, 웹훅 1건")
        void concurrent_same_key_creates_one_payment() throws Exception {
            // given
            int threads = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<PaymentView>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return service.approve(REQUEST);
                }));
            }

            // when
            start.countDown();
            List<String> tids = new ArrayList<>();
            for (Future<PaymentView> result : results) {
                tids.add(result.get(5, TimeUnit.SECONDS).pgTid());
            }
            pool.shutdown();

            // then
            assertThat(tids).containsOnly(tids.getFirst());
            assertThat(published).hasSize(1);
        }
    }

    @Nested
    @DisplayName("취소 요청이 오면")
    class Cancel {

        @Test
        @DisplayName("일부 취소 후 남은 금액을 취소하면 PARTIALLY_CANCELLED → CANCELLED")
        void partial_then_full() {
            // given
            String tid = service.approve(REQUEST).pgTid();

            // when
            CancelResult partial = service.cancel(new CancelCommand(tid, "cancel-1", 4_000));
            CancelResult rest = service.cancel(new CancelCommand(tid, "cancel-2", 5_900));

            // then
            assertThat(partial.status()).isEqualTo(MockPaymentStatus.PARTIALLY_CANCELLED);
            assertThat(partial.totalCancelledAmount()).isEqualTo(4_000);
            assertThat(rest.status()).isEqualTo(MockPaymentStatus.CANCELLED);
            assertThat(rest.totalCancelledAmount()).isEqualTo(9_900);
        }

        @Test
        @DisplayName("같은 취소 키로 다시 오면 한 번만 반영하고 같은 결과를 돌려준다")
        void same_cancel_key_applies_once() {
            // given
            String tid = service.approve(REQUEST).pgTid();
            CancelCommand command = new CancelCommand(tid, "cancel-1", 4_000);
            service.cancel(command);

            // when
            CancelResult again = service.cancel(command);

            // then
            assertThat(again.totalCancelledAmount()).isEqualTo(4_000);
            assertThat(service.find(REQUEST.idempotencyKey())).get()
                    .extracting(PaymentView::cancelledAmount).isEqualTo(4_000L);
        }

        @Test
        @DisplayName("남은 금액보다 많이 취소하면 400 CANCEL_AMOUNT_EXCEEDED")
        void rejects_exceeding_amount() {
            // given
            String tid = service.approve(REQUEST).pgTid();

            // when // then
            assertThatThrownBy(() -> service.cancel(new CancelCommand(tid, "cancel-1", 10_000)))
                    .isInstanceOfSatisfying(MockPgException.class,
                            e -> assertThat(e.getCode()).isEqualTo("CANCEL_AMOUNT_EXCEEDED"));
        }

        @Test
        @DisplayName("전액 취소된 결제에 새 취소 키로 요청하면 409 NOT_CANCELLABLE")
        void rejects_after_full_cancel() {
            // given
            String tid = service.approve(REQUEST).pgTid();
            service.cancel(new CancelCommand(tid, "cancel-1", 9_900));

            // when // then
            assertThatThrownBy(() -> service.cancel(new CancelCommand(tid, "cancel-2", 1)))
                    .isInstanceOfSatisfying(MockPgException.class,
                            e -> assertThat(e.getCode()).isEqualTo("NOT_CANCELLABLE"));
        }

        @Test
        @DisplayName("모르는 거래 ID면 404 PAYMENT_NOT_FOUND")
        void unknown_tid() {
            // when // then
            assertThatThrownBy(() -> service.cancel(new CancelCommand("pg_unknown", "cancel-1", 1)))
                    .isInstanceOfSatisfying(MockPgException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PAYMENT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("상태 조회는")
    class Find {

        @Test
        @DisplayName("받은 적 없는 멱등 키면 비어 있다")
        void unknown_key_is_empty() {
            // when // then
            assertThat(service.find("ORD-20261020-ZZZZ9999-1")).isEmpty();
        }
    }
}
