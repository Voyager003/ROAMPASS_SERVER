package com.roampass_server.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.admin.application.AdminOrderQueryService;
import com.roampass_server.admin.application.AdminOrderSearchCondition;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.config.ClockConfig;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.PaymentStatus;

@WebMvcTest(controllers = AdminOrderController.class)
@Import(ClockConfig.class)
@DisplayName("어드민 주문 API")
class AdminOrderControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminOrderQueryService queryService;

    @Test
    @DisplayName("X-Admin-Id가 없으면 401 MISSING_ADMIN_ID")
    void requires_admin() {
        // when // then
        assertThat(mvc.get().uri("/admin/orders")).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("MISSING_ADMIN_ID");
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("필터를 검색 조건으로 넘기고, 응답에 조회 시각 asOf를 담는다")
    void searches_with_filters() {
        // given
        given(queryService.search(any(), anyInt(), anyInt())).willReturn(new PageResult<>(List.of(), 1, 10, 0));

        // when
        var result = mvc.get().uri("/admin/orders?status=ISSUED&paymentStatus=APPROVED&installStatus=INSTALLED"
                + "&simType=ESIM&from=2026-10-20T00:00:00Z&to=2026-10-21T00:00:00Z&page=1&size=10")
                .header("X-Admin-Id", "admin-1");

        // then
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.asOf").isNotNull();
        verify(queryService).search(new AdminOrderSearchCondition(OrderStatus.ISSUED, PaymentStatus.APPROVED,
                InstallStatus.INSTALLED, SimType.ESIM, Instant.parse("2026-10-20T00:00:00Z"),
                Instant.parse("2026-10-21T00:00:00Z")), 1, 10);
    }

    @Test
    @DisplayName("페이지 크기가 50을 넘으면 400 INVALID_PAGE")
    void rejects_large_page() {
        // when // then
        assertThat(mvc.get().uri("/admin/orders?size=51").header("X-Admin-Id", "admin-1"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_PAGE");
    }

    @Test
    @DisplayName("상세: 없는 주문이면 404")
    void detail_not_found() {
        // given
        given(queryService.detail(eq(OrderNo.of("ORD-20261021-NONE0000"))))
                .willThrow(new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

        // when // then
        assertThat(mvc.get().uri("/admin/orders/ORD-20261021-NONE0000").header("X-Admin-Id", "admin-1"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
