package com.roampass_server.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.order.application.OrderExpirationService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.MutableClock;

/**
 * 주문 생성(API) → 결제 대기 시간 경과 → 만료 처리 → 저장 상태·이력 확인
 * 시계를 앞으로 돌려야 해서 전용 Clock 빈 사용(다른 통합 테스트와 컨텍스트 분리)
 */
@IntegrationTest
@Import(OrderExpirationE2ETest.ClockOverride.class)
@DisplayName("E2E: 결제 대기 만료")
class OrderExpirationE2ETest {

    private static final Instant START = Instant.parse("2026-10-20T03:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockOverride {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private OrderExpirationService expirationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Test
    @DisplayName("결제 없이 10분이 지나면 만료 처리되어 EXPIRED로 저장되고 이력 2건(생성·만료)이 남는다")
    void pending_order_expires_after_payment_window() {
        // given
        MvcTestResult created = mvc.post().uri("/api/orders").header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"planId": 1, "travelStartDate": "2026-10-25"}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String location = created.getResponse().getHeader("Location");
        OrderNo orderNo = OrderNo.of(location.substring(location.lastIndexOf('/') + 1));

        // when: 만료 직전에는 처리되지 않음
        clock.advance(Duration.ofMinutes(10).minusSeconds(1));
        expirationService.expireDue();

        // then
        assertThat(orderRepository.findByOrderNo(orderNo)).get()
                .extracting(order -> order.status()).isEqualTo(OrderStatus.PENDING);

        // when: 결제 대기 시간 도달
        clock.advance(Duration.ofSeconds(1));
        expirationService.expireDue();

        // then
        assertThat(orderRepository.findByOrderNo(orderNo)).get()
                .extracting(order -> order.status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(mvc.get().uri(location).header("X-User-Id", "user-1"))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("EXPIRED");
        assertThat(historyRepository.findByOrderNo(orderNo))
                .extracting(StatusHistory::fromStatus, StatusHistory::toStatus, StatusHistory::actor)
                .containsExactly(
                        tuple(null, "PENDING", Actor.user(UserId.of("user-1"))),
                        tuple("PENDING", "EXPIRED", Actor.system()));
    }
}
