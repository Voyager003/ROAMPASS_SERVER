package com.roampass_server.issuance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.QrViewService;

@WebMvcTest(controllers = SimController.class)
@DisplayName("POST /api/sims/{orderNo}/qr-viewed")
class SimControllerTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private QrViewService qrViewService;

    @Test
    @DisplayName("기록 후 204")
    void records() {
        // when
        var result = mvc.post().uri("/api/sims/ORD-20261021-AAAA1111/qr-viewed").header("X-User-Id", "user-1");

        // then
        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        verify(qrViewService).markViewed(UserId.of("user-1"), ORDER_NO);
    }

    @Test
    @DisplayName("발급 전이면 404 SIM_NOT_FOUND, 헤더가 없으면 401")
    void errors() {
        // given
        willThrow(new NotFoundException("SIM_NOT_FOUND", "not issued"))
                .given(qrViewService).markViewed(UserId.of("user-1"), ORDER_NO);

        // when // then
        assertThat(mvc.post().uri("/api/sims/ORD-20261021-AAAA1111/qr-viewed").header("X-User-Id", "user-1"))
                .hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("SIM_NOT_FOUND");
        assertThat(mvc.post().uri("/api/sims/ORD-20261021-AAAA1111/qr-viewed"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
