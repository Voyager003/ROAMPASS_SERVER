package com.roampass_server.admin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.roampass_server.admin.application.AdminActionResult;
import com.roampass_server.admin.application.AdminActionService;
import com.roampass_server.admin.application.AdminIssueQueryService;
import com.roampass_server.admin.application.AdminIssues;
import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.config.ClockConfig;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.order.domain.model.OrderStatus;

@WebMvcTest(controllers = AdminIssueController.class)
@Import(ClockConfig.class)
@DisplayName("어드민 이상 건·조치 API")
class AdminIssueControllerTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AdminIssueQueryService issueQueryService;

    @MockitoBean
    private AdminActionService actionService;

    private MvcTestResult refund(String reason) {
        return mvc.post().uri("/admin/orders/ORD-20261021-AAAA1111/refund").header("X-Admin-Id", "admin-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"reason":"%s"}
                        """.formatted(reason)).exchange();
    }

    @Test
    @DisplayName("이상 건 목록을 asOf와 함께 돌려준다")
    void issues() {
        // given
        given(issueQueryService.find()).willReturn(new AdminIssues(List.of(), List.of(), List.of(), List.of(), 5));

        // when // then
        assertThat(mvc.get().uri("/admin/issues").header("X-Admin-Id", "admin-1")).hasStatusOk().bodyJson()
                .extractingPath("$.data.stuckThresholdMinutes").isEqualTo(5);
    }

    @Test
    @DisplayName("수동 환불: 운영자 ID와 사유를 넘기고 결과를 200으로")
    void refunds() {
        // given
        given(actionService.refund(AdminId.of("admin-1"), ORDER_NO, "고객 요청"))
                .willReturn(new AdminActionResult(ORDER_NO.value(), OrderStatus.REFUNDING));

        // when // then
        assertThat(refund("고객 요청")).hasStatusOk().bodyJson()
                .extractingPath("$.data.orderStatus").isEqualTo("REFUNDING");
    }

    @Test
    @DisplayName("사유 누락 400, 다른 운영자가 먼저 처리했으면 409 ACTION_CONFLICT, 헤더 없으면 401")
    void errors() {
        // given
        given(actionService.refund(any(), eq(ORDER_NO), eq("")))
                .willThrow(new InvalidValueException("ADMIN_REASON_REQUIRED", "reason"));
        given(actionService.refund(any(), eq(ORDER_NO), eq("늦은 클릭")))
                .willThrow(new ConflictException("ACTION_CONFLICT", "changed"));

        // when // then
        assertThat(refund("")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("ADMIN_REASON_REQUIRED");
        assertThat(refund("늦은 클릭")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("ACTION_CONFLICT");
        assertThat(mvc.post().uri("/admin/orders/ORD-20261021-AAAA1111/reissue")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(issueQueryService);
    }
}
