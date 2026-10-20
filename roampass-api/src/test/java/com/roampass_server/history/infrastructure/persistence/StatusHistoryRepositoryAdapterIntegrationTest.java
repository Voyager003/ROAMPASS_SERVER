package com.roampass_server.history.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static com.roampass_server.support.PersistenceTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@Transactional
@DisplayName("StatusHistoryRepository (PostgreSQL)")
class StatusHistoryRepositoryAdapterIntegrationTest {

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    @DisplayName("주문 하나의 이력을 발생 시각 순으로 모든 값과 함께 돌려준다")
    void returns_history_of_one_order_in_time_order() {
        // given
        Order order = orderRepository.save(pendingOrder("HIST0001", NOW));
        Order other = orderRepository.save(pendingOrder("HIST0002", NOW));
        OrderNo orderNo = order.orderNo();
        historyRepository.append(StatusHistory.record(orderNo, HistoryTarget.order(orderNo), OrderStatus.PENDING,
                OrderStatus.EXPIRED, Actor.system(), "결제 대기 만료", NOW.plusSeconds(600)));
        historyRepository.append(StatusHistory.record(orderNo, HistoryTarget.order(orderNo), null,
                OrderStatus.PENDING, Actor.user(order.userId()), "주문 생성", NOW));
        historyRepository.append(StatusHistory.record(orderNo, HistoryTarget.of(TargetType.REFUND, "42"),
                OrderStatus.NEEDS_ATTENTION, OrderStatus.REFUNDING, Actor.admin(AdminId.of("admin-kim")),
                "고객 문의로 수동 환불", NOW.plusSeconds(900)));
        historyRepository.append(StatusHistory.record(other.orderNo(), HistoryTarget.order(other.orderNo()), null,
                OrderStatus.PENDING, Actor.user(other.userId()), "주문 생성", NOW));

        // when
        List<StatusHistory> histories = historyRepository.findByOrderNo(orderNo);

        // then
        assertThat(histories).extracting(StatusHistory::toStatus).containsExactly("PENDING", "EXPIRED", "REFUNDING");
        StatusHistory adminAction = histories.get(2);
        assertThat(adminAction.id()).isNotNull();
        assertThat(adminAction.target()).isEqualTo(HistoryTarget.of(TargetType.REFUND, "42"));
        assertThat(adminAction.fromStatus()).isEqualTo("NEEDS_ATTENTION");
        assertThat(adminAction.actor().asString()).isEqualTo("ADMIN:admin-kim");
        assertThat(adminAction.reason()).isEqualTo("고객 문의로 수동 환불");
        assertThat(adminAction.occurredAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(histories.get(0).fromStatus()).isNull();
    }
}
