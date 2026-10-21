package com.roampass_server.e2e;

import static com.roampass_server.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderIdempotencyKey;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.support.IntegrationTest;

/**
 * 주문 생성 재전송: 같은 사용자가 같은 Idempotency-Key로 주문 요청 8개를 동시에 보냄
 * (응답 유실·서버 재시작 뒤 재시도가 겹친 상황). 실제 PostgreSQL, 스레드마다 별도 트랜잭션 커밋
 */
@IntegrationTest
@DisplayName("동시성: 같은 멱등 키로 주문 생성")
class OrderIdempotencyConcurrencyTest {

    private static final int REQUESTS = 8;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Test
    @DisplayName("8개 동시 → 모두 같은 주문번호를 받고, 주문 1건·생성 이력 1건")
    void creates_one_order() throws Exception {
        // given
        UserId user = UserId.of("user-order-idem-" + UUID.randomUUID().toString().substring(0, 8));
        OrderIdempotencyKey key = OrderIdempotencyKey.of(UUID.randomUUID().toString());
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        List<Callable<OrderNo>> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> orderService.createOrder(user, 1L, travelDate, key).orderNo());
        }

        // when
        List<OrderNo> results = runConcurrently(tasks);

        // then
        assertThat(results).hasSize(REQUESTS).containsOnly(results.getFirst());
        assertThat(orderRepository.findByUser(user, EnumSet.allOf(OrderStatus.class), 0, 50).totalElements())
                .isEqualTo(1);
        assertThat(historyRepository.findByOrderNo(results.getFirst())).hasSize(1);
    }
}
