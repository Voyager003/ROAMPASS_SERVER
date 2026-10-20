package com.roampass_server.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.history.application.FakeStatusHistoryRepository;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.plan.application.FakePlanRepository;
import com.roampass_server.plan.application.PlanQueryService;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.model.PlanSpec;
import com.roampass_server.support.MutableClock;

@DisplayName("OrderService")
class OrderServiceTest {

    // 2026-10-20 12:00 KST
    private static final Instant START = Instant.parse("2026-10-20T03:00:00Z");
    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 10, 25);
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(10);
    private static final UserId USER = UserId.of("user-1");
    private static final OrderNo FIXED_ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");
    private static final Plan JP_ESIM = Plan.of(1L, PlanSpec.of("JP", 5, 3_072), SimType.ESIM, Money.krw(12_000));

    private MutableClock clock;
    private FakeOrderRepository orderRepository;
    private FakeStatusHistoryRepository historyRepository;
    private OrderService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        orderRepository = new FakeOrderRepository();
        historyRepository = new FakeStatusHistoryRepository();
        service = new OrderService(
                orderRepository,
                now -> FIXED_ORDER_NO,
                new PlanQueryService(new FakePlanRepository().add(JP_ESIM)),
                new StatusHistoryRecorder(historyRepository),
                clock,
                PAYMENT_WINDOW);
    }

    @Nested
    @DisplayName("주문을 만들면")
    class CreateOrder {

        @Test
        @DisplayName("PENDING 주문이 저장되고 요금제 가격·만료 시각이 담긴 결과를 돌려준다")
        void creates_pending_order() {
            // when
            OrderResult result = service.createOrder(USER, 1L, TRAVEL_DATE);

            // then
            assertThat(result.orderNo()).isEqualTo(FIXED_ORDER_NO);
            assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.planId()).isEqualTo(1L);
            assertThat(result.simType()).isEqualTo(SimType.ESIM);
            assertThat(result.amount()).isEqualTo(Money.krw(12_000));
            assertThat(result.travelStartDate()).isEqualTo(TRAVEL_DATE);
            assertThat(result.createdAt()).isEqualTo(START);
            assertThat(result.expiresAt()).isEqualTo(START.plus(PAYMENT_WINDOW));
            assertThat(orderRepository.findByOrderNo(FIXED_ORDER_NO)).isPresent();
        }

        @Test
        @DisplayName("주문 생성 이력이 사용자 주체로 1건 남는다")
        void records_creation_history() {
            // when
            service.createOrder(USER, 1L, TRAVEL_DATE);

            // then
            List<StatusHistory> histories = historyRepository.findByOrderNo(FIXED_ORDER_NO);
            assertThat(histories).hasSize(1);
            StatusHistory history = histories.get(0);
            assertThat(history.target()).isEqualTo(HistoryTarget.order(FIXED_ORDER_NO));
            assertThat(history.fromStatus()).isNull();
            assertThat(history.toStatus()).isEqualTo("PENDING");
            assertThat(history.actor()).isEqualTo(Actor.user(USER));
            assertThat(history.occurredAt()).isEqualTo(START);
        }

        @Test
        @DisplayName("없는 요금제면 PLAN_NOT_FOUND이고 주문도 이력도 남지 않는다")
        void unknown_plan() {
            // when & then
            assertThatThrownBy(() -> service.createOrder(USER, 999L, TRAVEL_DATE))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo("PLAN_NOT_FOUND");
            assertThat(orderRepository.count()).isZero();
            assertThat(historyRepository.all()).isEmpty();
        }

        @Test
        @DisplayName("여행 시작일이 과거면 INVALID_TRAVEL_START_DATE이고 주문도 이력도 남지 않는다")
        void past_travel_date() {
            // when & then
            assertThatThrownBy(() -> service.createOrder(USER, 1L, LocalDate.of(2026, 10, 19)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_TRAVEL_START_DATE");
            assertThat(orderRepository.count()).isZero();
            assertThat(historyRepository.all()).isEmpty();
        }
    }

    @Nested
    @DisplayName("내 주문을 조회하면")
    class GetMyOrder {

        @BeforeEach
        void createOrder() {
            service.createOrder(USER, 1L, TRAVEL_DATE);
        }

        @Test
        @DisplayName("본인 주문이면 돌려준다")
        void returns_own_order() {
            // when
            OrderResult result = service.getMyOrder(USER, FIXED_ORDER_NO);

            // then
            assertThat(result.orderNo()).isEqualTo(FIXED_ORDER_NO);
            assertThat(result.status()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("다른 사용자의 주문이면 존재를 드러내지 않고 ORDER_NOT_FOUND")
        void hides_other_users_order() {
            // when & then
            assertThatThrownBy(() -> service.getMyOrder(UserId.of("user-2"), FIXED_ORDER_NO))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo("ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("없는 주문번호면 ORDER_NOT_FOUND")
        void unknown_order() {
            // when & then
            assertThatThrownBy(() -> service.getMyOrder(USER, OrderNo.of("ORD-20261020-ZZZZ9999")))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo("ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("결제 대기 시간이 지나면 만료 스케줄러가 돌기 전이라도 EXPIRED로 보인다")
        void shows_expired_before_scheduler_runs() {
            // given
            clock.advance(PAYMENT_WINDOW);

            // when
            OrderResult result = service.getMyOrder(USER, FIXED_ORDER_NO);

            // then
            assertThat(result.status()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(orderRepository.findByOrderNo(FIXED_ORDER_NO).orElseThrow().status())
                    .isEqualTo(OrderStatus.PENDING); // 저장된 상태는 그대로
        }
    }
}
