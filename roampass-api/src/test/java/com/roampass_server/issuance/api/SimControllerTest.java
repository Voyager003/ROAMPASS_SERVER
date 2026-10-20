package com.roampass_server.issuance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.QrViewService;
import com.roampass_server.issuance.application.UsimRegistrationResult;
import com.roampass_server.issuance.application.UsimRegistrationService;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.exception.InvalidValueException;
import org.springframework.http.MediaType;

@WebMvcTest(controllers = SimController.class)
@DisplayName("SIM API (QR 열람·유심 등록)")
class SimControllerTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private QrViewService qrViewService;

    @MockitoBean
    private UsimRegistrationService usimRegistrationService;

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

    @Test
    @DisplayName("유심 등록: 결과를 200으로, 잘못된 ICCID 400, 중복 ICCID 409")
    void register_usim() {
        // given
        given(usimRegistrationService.register(UserId.of("user-1"), ORDER_NO, "8982300912345678902"))
                .willReturn(new UsimRegistrationResult(ORDER_NO.value(), "8982300912345678902", InstallStatus.ACTIVE));
        given(usimRegistrationService.register(UserId.of("user-1"), ORDER_NO, "123"))
                .willThrow(new InvalidValueException("INVALID_ICCID", "bad"));
        given(usimRegistrationService.register(UserId.of("user-1"), ORDER_NO, "8982300912345678910"))
                .willThrow(new ConflictException("ICCID_ALREADY_REGISTERED", "dup"));

        // when // then
        assertThat(register("8982300912345678902")).hasStatusOk()
                .bodyJson().extractingPath("$.installStatus").isEqualTo("ACTIVE");
        assertThat(register("123")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_ICCID");
        assertThat(register("8982300912345678910")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("ICCID_ALREADY_REGISTERED");
    }

    private MvcTestResult register(String iccid) {
        return mvc.post().uri("/api/orders/ORD-20261021-AAAA1111/usim/register").header("X-User-Id", "user-1")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"iccid":"%s"}
                        """.formatted(iccid)).exchange();
    }
}
