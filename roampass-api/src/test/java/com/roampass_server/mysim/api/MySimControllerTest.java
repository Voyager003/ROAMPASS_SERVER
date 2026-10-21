package com.roampass_server.mysim.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.mysim.application.MySimDetail;
import com.roampass_server.mysim.application.MySimQueryService;
import com.roampass_server.order.domain.model.OrderStatus;

@WebMvcTest(controllers = MySimController.class)
@DisplayName("내 SIM API")
class MySimControllerTest {

    private static final UserId USER = UserId.of("user-1");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MySimQueryService queryService;

    @Test
    @DisplayName("X-User-Id가 없으면 401 MISSING_USER_ID")
    void requires_user() {
        // when // then
        assertThat(mvc.get().uri("/api/sims")).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("MISSING_USER_ID");
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("목록: 본인 기준으로 페이지를 조회한다")
    void lists() {
        // given
        given(queryService.list(USER, 1, 10)).willReturn(new PageResult<>(List.of(), 1, 10, 0));

        // when
        var result = mvc.get().uri("/api/sims?page=1&size=10").header("X-User-Id", "user-1");

        // then
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.page").isEqualTo(1);
        verify(queryService).list(USER, 1, 10);
    }

    @Test
    @DisplayName("목록: 페이지 크기가 50을 넘으면 400 INVALID_PAGE")
    void rejects_large_page() {
        // when // then
        assertThat(mvc.get().uri("/api/sims?size=51").header("X-User-Id", "user-1"))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("INVALID_PAGE");
        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("상세: 결제 금액·환불 가능 여부를 돌려준다 (발급 전이면 sim은 null)")
    void detail() {
        // given
        given(queryService.detail(USER, ORDER_NO)).willReturn(new MySimDetail(ORDER_NO.value(), 1L, SimType.ESIM,
                Money.krw(9_900), LocalDate.of(2026, 10, 25), OrderStatus.PAID,
                Instant.parse("2026-10-21T00:00:00Z"), null, true, null, null));

        // when
        var result = mvc.get().uri("/api/sims/ORD-20261021-AAAA1111").header("X-User-Id", "user-1");

        // then
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.refundable").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.amount.amount").isEqualTo(9900);
        assertThat(result).bodyJson().extractingPath("$.sim").isNull();
    }

    @Test
    @DisplayName("상세: 없거나 남의 주문이면 404")
    void detail_not_found() {
        // given
        given(queryService.detail(USER, ORDER_NO)).willThrow(new NotFoundException("ORDER_NOT_FOUND", "not found"));

        // when // then
        assertThat(mvc.get().uri("/api/sims/ORD-20261021-AAAA1111").header("X-User-Id", "user-1"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("ORDER_NOT_FOUND");
    }
}
