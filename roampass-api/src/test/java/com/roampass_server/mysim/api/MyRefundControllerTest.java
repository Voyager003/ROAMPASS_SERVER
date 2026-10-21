package com.roampass_server.mysim.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.mysim.application.MyRefundResult;
import com.roampass_server.mysim.application.MyRefundService;
import com.roampass_server.order.domain.model.OrderStatus;

@WebMvcTest(controllers = MyRefundController.class)
@DisplayName("사용자 환불 API")
class MyRefundControllerTest {

    private static final UserId USER = UserId.of("user-1");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");
    private static final String URI = "/api/orders/ORD-20261021-AAAA1111/refund";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MyRefundService refundService;

    @Test
    @DisplayName("접수되면 202와 REFUNDING")
    void accepted() {
        // given
        given(refundService.refund(USER, ORDER_NO))
                .willReturn(new MyRefundResult(ORDER_NO.value(), OrderStatus.REFUNDING));

        // when // then
        assertThat(mvc.post().uri(URI).header("X-User-Id", "user-1"))
                .hasStatus(HttpStatus.ACCEPTED).bodyJson().extractingPath("$.orderStatus").isEqualTo("REFUNDING");
    }

    @Test
    @DisplayName("환불할 수 없는 상태면 409와 code")
    void conflict() {
        // given
        given(refundService.refund(USER, ORDER_NO))
                .willThrow(new ConflictException("SIM_NOT_REFUNDABLE", "installed"));

        // when // then
        assertThat(mvc.post().uri(URI).header("X-User-Id", "user-1"))
                .hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("SIM_NOT_REFUNDABLE");
    }

    @Test
    @DisplayName("X-User-Id가 없으면 401")
    void requires_user() {
        // when // then
        assertThat(mvc.post().uri(URI)).hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(refundService);
    }
}
