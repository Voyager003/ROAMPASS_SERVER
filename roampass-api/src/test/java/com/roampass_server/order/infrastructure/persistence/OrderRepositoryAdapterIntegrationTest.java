package com.roampass_server.order.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static com.roampass_server.support.PersistenceTestFixtures.WINDOW;
import static com.roampass_server.support.PersistenceTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.order.domain.repository.OrderSearchCondition;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.PersistenceTestFixtures;

@IntegrationTest
@Transactional
@DisplayName("OrderRepository (PostgreSQL)")
class OrderRepositoryAdapterIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Nested
    @DisplayName("저장하고 다시 읽으면")
    class SaveAndFind {

        @Test
        @DisplayName("id가 부여되고 모든 값이 그대로 돌아온다")
        void round_trips_all_fields() {
            // given
            Order order = pendingOrder("RTRP0001", NOW);

            // when
            Order saved = orderRepository.save(order);
            Order found = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();

            // then
            assertThat(saved.id()).isNotNull();
            assertThat(found.id()).isEqualTo(saved.id());
            assertThat(found.userId()).isEqualTo(PersistenceTestFixtures.USER);
            assertThat(found.planId()).isEqualTo(1L);
            assertThat(found.simType()).isEqualTo(SimType.ESIM);
            assertThat(found.amount()).isEqualTo(Money.krw(9_900));
            assertThat(found.travelStartDate()).isEqualTo(PersistenceTestFixtures.TRAVEL_DATE);
            assertThat(found.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(found.createdAt()).isEqualTo(NOW);
            assertThat(found.expiresAt()).isEqualTo(NOW.plus(WINDOW));
            assertThat(found.issuedAt()).isNull();
        }

        @Test
        @DisplayName("같은 주문번호를 두 번 저장하면 유니크 제약으로 실패한다")
        void duplicate_order_no_is_rejected() {
            // given
            orderRepository.save(pendingOrder("DUPL0001", NOW));

            // when & then
            assertThatThrownBy(() -> orderRepository.save(pendingOrder("DUPL0001", NOW)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("만료 대상을 찾으면")
    class FindExpirable {

        @Test
        @DisplayName("만료 시각이 지난 PENDING 주문만 만료 시각 순으로 묶음 크기만큼 나온다")
        void finds_due_pending_orders_in_order() {
            // given
            Order later = orderRepository.save(pendingOrder("EXPR0002", NOW.plusSeconds(60)));
            Order earlier = orderRepository.save(pendingOrder("EXPR0001", NOW));
            orderRepository.save(pendingOrder("EXPR0003", NOW.plusSeconds(3_600))); // 아직 만료 전
            Order paid = orderRepository.save(pendingOrder("EXPR0004", NOW));
            orderRepository.saveIfStatus(paid.confirmPayment(NOW.plusSeconds(10)), OrderStatus.PENDING);

            // when
            List<Order> due = orderRepository.findExpirable(NOW.plus(WINDOW).plusSeconds(60), 10);
            List<Order> limited = orderRepository.findExpirable(NOW.plus(WINDOW).plusSeconds(60), 1);

            // then
            assertThat(due).extracting(Order::orderNo).containsExactly(earlier.orderNo(), later.orderNo());
            assertThat(limited).extracting(Order::orderNo).containsExactly(earlier.orderNo());
        }
    }

    @Nested
    @DisplayName("조건부로 저장하면")
    class SaveIfStatus {

        @Test
        @DisplayName("저장된 상태가 기대한 상태면 변경이 저장되고 true")
        void updates_when_status_matches() {
            // given
            Order order = orderRepository.save(pendingOrder("COND0001", NOW));

            // when
            boolean updated = orderRepository.saveIfStatus(order.expire(NOW.plus(WINDOW)), OrderStatus.PENDING);

            // then
            assertThat(updated).isTrue();
            assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status()).isEqualTo(OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("그 사이 상태가 바뀌었으면 아무것도 바꾸지 않고 false (먼저 반영된 쪽이 이김)")
        void does_nothing_when_status_changed() {
            // given — 결제 확정이 먼저 반영됨
            Order order = orderRepository.save(pendingOrder("COND0002", NOW));
            orderRepository.saveIfStatus(order.confirmPayment(NOW.plusSeconds(10)), OrderStatus.PENDING);

            // when — 만료 쪽은 예전 PENDING 스냅숏을 들고 시도
            boolean updated = orderRepository.saveIfStatus(order.expire(NOW.plus(WINDOW)), OrderStatus.PENDING);

            // then
            assertThat(updated).isFalse();
            assertThat(orderRepository.findByOrderNo(order.orderNo()).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("발급 시각도 함께 저장된다")
        void persists_issued_at() {
            // given
            Order order = orderRepository.save(pendingOrder("COND0003", NOW));
            Order paid = order.confirmPayment(NOW.plusSeconds(10));
            orderRepository.saveIfStatus(paid, OrderStatus.PENDING);
            Order requested = paid.requestIssuance();
            orderRepository.saveIfStatus(requested, OrderStatus.PAID);

            // when
            orderRepository.saveIfStatus(requested.markIssued(NOW.plusSeconds(120)), OrderStatus.ISSUE_REQUESTED);

            // then
            Order found = orderRepository.findByOrderNo(order.orderNo()).orElseThrow();
            assertThat(found.status()).isEqualTo(OrderStatus.ISSUED);
            assertThat(found.issuedAt()).isEqualTo(NOW.plusSeconds(120));
        }
    }

    @Nested
    @DisplayName("어드민 검색은")
    class Search {

        @Test
        @DisplayName("상태·SIM 종류·생성 기간으로 거르고 최신순으로 페이지를 나눈다")
        void filters_sorts_and_pages() {
            // given
            orderRepository.save(pendingOrder("SRCH0001", NOW));
            orderRepository.save(pendingOrder("SRCH0002", NOW.plusSeconds(60)));
            Order paid = orderRepository.save(pendingOrder("SRCH0003", NOW.plusSeconds(120)));
            orderRepository.saveIfStatus(paid.confirmPayment(NOW.plusSeconds(120)), OrderStatus.PENDING);
            OrderSearchCondition pendingInRange = new OrderSearchCondition(OrderStatus.PENDING,
                    SimType.ESIM, NOW, NOW.plusSeconds(3_600));

            // when
            var firstPage = orderRepository.search(pendingInRange, 0, 1);
            var all = orderRepository.search(pendingInRange, 0, 10);

            // then
            assertThat(firstPage.totalElements()).isEqualTo(2);
            assertThat(firstPage.content()).extracting(o -> o.orderNo().value())
                    .containsExactly("ORD-20261020-SRCH0002");
            assertThat(all.content()).extracting(o -> o.orderNo().value())
                    .containsExactly("ORD-20261020-SRCH0002", "ORD-20261020-SRCH0001");
        }
    }

    @Nested
    @DisplayName("사용자별 조회는")
    class FindByUser {

        @Test
        @DisplayName("본인 주문 중 주어진 상태인 것만 최신순으로 페이지를 나눈다")
        void filters_by_owner_and_status() {
            // given
            Order first = orderRepository.save(pendingOrder("USER0001", NOW));
            orderRepository.saveIfStatus(first.confirmPayment(NOW), OrderStatus.PENDING);
            Order second = orderRepository.save(pendingOrder("USER0002", NOW.plusSeconds(60)));
            orderRepository.saveIfStatus(second.confirmPayment(NOW.plusSeconds(60)), OrderStatus.PENDING);
            orderRepository.save(pendingOrder("USER0003", NOW.plusSeconds(120))); // 결제 대기
            Order others = orderRepository.save(Order.create(OrderNo.of("ORD-20261020-OTHR0001"), UserId.of("user-2"),
                    PersistenceTestFixtures.SEED_PLAN_1, PersistenceTestFixtures.TRAVEL_DATE, NOW, WINDOW));
            orderRepository.saveIfStatus(others.confirmPayment(NOW), OrderStatus.PENDING);

            // when
            var firstPage = orderRepository.findByUser(PersistenceTestFixtures.USER, EnumSet.of(OrderStatus.PAID), 0, 1);
            var all = orderRepository.findByUser(PersistenceTestFixtures.USER, EnumSet.of(OrderStatus.PAID), 0, 10);

            // then
            assertThat(firstPage.totalElements()).isEqualTo(2);
            assertThat(firstPage.content()).extracting(o -> o.orderNo().value())
                    .containsExactly("ORD-20261020-USER0002");
            assertThat(all.content()).extracting(o -> o.orderNo().value())
                    .containsExactly("ORD-20261020-USER0002", "ORD-20261020-USER0001");
        }
    }
}
