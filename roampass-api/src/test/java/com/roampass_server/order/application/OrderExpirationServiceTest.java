package com.roampass_server.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.application.FakeStatusHistoryRepository;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.model.PlanSnapshot;
import com.roampass_server.support.MutableClock;

@DisplayName("OrderExpirationService")
class OrderExpirationServiceTest {

    // 2026-10-20 12:00 KST
    private static final Instant START = Instant.parse("2026-10-20T03:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final PlanSnapshot PLAN = PlanSnapshot.of(1L, SimType.ESIM, Money.krw(12_000));
    private static final UserId USER = UserId.of("user-1");

    private MutableClock clock;
    private FakeOrderRepository orderRepository;
    private FakeStatusHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        orderRepository = new FakeOrderRepository();
        historyRepository = new FakeStatusHistoryRepository();
    }

    private OrderExpirationService serviceWithBatch(int batchSize) {
        return new OrderExpirationService(orderRepository, new StatusHistoryRecorder(historyRepository), clock, batchSize);
    }

    private Order pendingOrder(String suffix, Instant createdAt) {
        Order order = Order.create(OrderNo.of("ORD-20261020-" + suffix), USER, PLAN, LocalDate.of(2026, 10, 25),
                createdAt, WINDOW);
        orderRepository.save(order);
        return order;
    }

    @Nested
    @DisplayName("만료 처리를 실행하면")
    class WhenRun {

        @Test
        @DisplayName("결제 대기 시간이 지난 주문만 EXPIRED가 되고 처리 건수를 돌려준다")
        void expires_only_due_orders() {
            // given
            Order due = pendingOrder("AAAA1111", START);
            Order notYet = pendingOrder("BBBB2222", START.plusSeconds(300));
            clock.advance(WINDOW); // due는 정확히 만료 시각, notYet은 5분 남음

            // when
            int expired = serviceWithBatch(100).expireDue();

            // then
            assertThat(expired).isEqualTo(1);
            assertThat(orderRepository.findByOrderNo(due.orderNo()).orElseThrow().status()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(orderRepository.findByOrderNo(notYet.orderNo()).orElseThrow().status()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("만료된 주문마다 시스템 주체의 이력이 남는다")
        void records_system_history() {
            // given
            Order due = pendingOrder("AAAA1111", START);
            clock.advance(WINDOW);

            // when
            serviceWithBatch(100).expireDue();

            // then
            List<StatusHistory> histories = historyRepository.findByOrderNo(due.orderNo());
            assertThat(histories).hasSize(1);
            assertThat(histories.get(0).fromStatus()).isEqualTo("PENDING");
            assertThat(histories.get(0).toStatus()).isEqualTo("EXPIRED");
            assertThat(histories.get(0).actor()).isEqualTo(Actor.system());
            assertThat(histories.get(0).occurredAt()).isEqualTo(START.plus(WINDOW));
        }

        @Test
        @DisplayName("한 번에 묶음 크기만큼만 처리하고 나머지는 다음 실행에서 처리한다")
        void respects_batch_size() {
            // given
            pendingOrder("AAAA1111", START);
            pendingOrder("BBBB2222", START);
            pendingOrder("CCCC3333", START);
            clock.advance(WINDOW);
            OrderExpirationService service = serviceWithBatch(2);

            // when
            int first = service.expireDue();
            int second = service.expireDue();

            // then
            assertThat(first).isEqualTo(2);
            assertThat(second).isEqualTo(1);
        }

        @Test
        @DisplayName("만료 대상이 없으면 0")
        void nothing_due() {
            // given
            pendingOrder("AAAA1111", START);

            // when & then
            assertThat(serviceWithBatch(100).expireDue()).isZero();
            assertThat(historyRepository.all()).isEmpty();
        }
    }

    @Nested
    @DisplayName("대상을 고른 뒤 저장 전에 다른 쪽이 상태를 바꾸면")
    class WhenChangedConcurrently {

        @Test
        @DisplayName("그 주문은 건너뛰고 만료 이력도 남기지 않는다 (결제 확정이 이김)")
        void skips_changed_order() {
            // given — 만료 대상으로 골라진 직후 결제 확정이 먼저 반영되는 상황
            Order due = pendingOrder("AAAA1111", START);
            clock.advance(WINDOW);
            FakeOrderRepository racingRepository = new FakeOrderRepository() {
                @Override
                public List<Order> findExpirable(Instant now, int limit) {
                    List<Order> candidates = orderRepository.findExpirable(now, limit);
                    orderRepository.replace(due.confirmPayment(START.plusSeconds(30))); // 만료 전 승인이 먼저 커밋됨
                    return candidates;
                }

                @Override
                public boolean saveIfStatus(Order order, OrderStatus expected) {
                    return orderRepository.saveIfStatus(order, expected);
                }
            };
            OrderExpirationService service =
                    new OrderExpirationService(racingRepository, new StatusHistoryRecorder(historyRepository), clock, 100);

            // when
            int expired = service.expireDue();

            // then
            assertThat(expired).isZero();
            assertThat(orderRepository.findByOrderNo(due.orderNo()).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
            assertThat(historyRepository.all()).isEmpty();
        }
    }
}
