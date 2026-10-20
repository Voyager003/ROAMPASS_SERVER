package com.roampass_server.order.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.order.application.OrderResult;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;

@WebMvcTest(controllers = OrderController.class)
@DisplayName("주문 API")
class OrderControllerTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");
    private static final UserId USER = UserId.of("user-1");
    private static final OrderResult RESULT = new OrderResult(ORDER_NO, 1L, SimType.ESIM, Money.krw(9_900),
            LocalDate.of(2026, 10, 25), OrderStatus.PENDING,
            Instant.parse("2026-10-20T03:00:00Z"), Instant.parse("2026-10-20T03:10:00Z"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OrderService orderService;

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        private final String validBody = """
                {"planId": 1, "travelStartDate": "2026-10-25"}
                """;

        @Test
        @DisplayName("주문이 만들어지면 201, Location 헤더, 주문 정보를 돌려준다 (시각은 UTC ISO-8601)")
        void creates_order() {
            // given
            given(orderService.createOrder(USER, 1L, LocalDate.of(2026, 10, 25))).willReturn(RESULT);

            // when
            var result = mvc.post().uri("/api/orders").header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON).content(validBody);

            // then
            assertThat(result).hasStatus(HttpStatus.CREATED)
                    .hasHeader("Location", "/api/orders/ORD-20261020-AAAA1111")
                    .bodyJson().isLenientlyEqualTo("""
                            {"orderNo":"ORD-20261020-AAAA1111","planId":1,"simType":"ESIM",
                             "amount":{"amount":9900,"currency":"KRW"},"travelStartDate":"2026-10-25",
                             "status":"PENDING","createdAt":"2026-10-20T03:00:00Z","expiresAt":"2026-10-20T03:10:00Z"}
                            """);
        }

        @Test
        @DisplayName("X-User-Id가 없으면 401이고 서비스를 호출하지 않는다")
        void requires_user_header() {
            // when
            var result = mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(validBody);

            // then
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("MISSING_USER_ID");
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("planId가 없으면 400 INVALID_REQUEST")
        void requires_plan_id() {
            // when
            var result = mvc.post().uri("/api/orders").header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"travelStartDate": "2026-10-25"}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("날짜 형식이 틀리면 400 INVALID_REQUEST")
        void rejects_malformed_date() {
            // when
            var result = mvc.post().uri("/api/orders").header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"planId": 1, "travelStartDate": "2026-13-45"}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }

        @Test
        @DisplayName("없는 요금제면 404 PLAN_NOT_FOUND")
        void unknown_plan() {
            // given
            given(orderService.createOrder(eq(USER), eq(999L), any()))
                    .willThrow(new NotFoundException("PLAN_NOT_FOUND", "Plan not found"));

            // when
            var result = mvc.post().uri("/api/orders").header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"planId": 999, "travelStartDate": "2026-10-25"}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("PLAN_NOT_FOUND");
        }

        @Test
        @DisplayName("여행 시작일 규칙을 어기면 도메인 코드 그대로 400 INVALID_TRAVEL_START_DATE")
        void invalid_travel_date() {
            // given
            given(orderService.createOrder(eq(USER), eq(1L), any()))
                    .willThrow(new InvalidValueException("INVALID_TRAVEL_START_DATE", "out of range"));

            // when
            var result = mvc.post().uri("/api/orders").header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON).content(validBody);

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_TRAVEL_START_DATE");
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{orderNo}")
    class GetOrder {

        @Test
        @DisplayName("본인 주문이면 200과 주문 정보")
        void returns_order() {
            // given
            given(orderService.getMyOrder(USER, ORDER_NO)).willReturn(RESULT);

            // when
            var result = mvc.get().uri("/api/orders/ORD-20261020-AAAA1111").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatusOk()
                    .bodyJson().extractingPath("$.status").isEqualTo("PENDING");
        }

        @Test
        @DisplayName("없거나 남의 주문이면 404 ORDER_NOT_FOUND")
        void not_found() {
            // given
            given(orderService.getMyOrder(USER, ORDER_NO))
                    .willThrow(new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

            // when
            var result = mvc.get().uri("/api/orders/ORD-20261020-AAAA1111").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("주문번호 형식이 틀리면 400 INVALID_ORDER_NO")
        void malformed_order_no() {
            // when
            var result = mvc.get().uri("/api/orders/not-an-order").header("X-User-Id", "user-1");

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_ORDER_NO");
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("X-User-Id가 없으면 401")
        void requires_user_header() {
            // when
            var result = mvc.get().uri("/api/orders/ORD-20261020-AAAA1111");

            // then
            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
