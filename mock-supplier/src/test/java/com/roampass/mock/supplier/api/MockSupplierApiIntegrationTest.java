package com.roampass.mock.supplier.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;
import com.roampass.mock.supplier.issuance.MockSupplierService;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "mock-supplier.callback.url=") // 콜백 발송 끔
@DisplayName("mock-supplier HTTP API")
class MockSupplierApiIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockSupplierService service;

    @AfterEach
    void resetFaults() {
        assertThat(mvc.post().uri("/mock/supplier/config/reset")).hasStatusOk();
    }

    private String issue(String key) {
        var result = mvc.post().uri("/mock/supplier/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idempotencyKey":"%s","orderNo":"ORD-20261020-AAAA1111"}
                        """.formatted(key))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.supplierOrderNo");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("POST /mock/supplier/orders")
    class Issue {

        @Test
        @DisplayName("202와 접수 번호를 돌려준다")
        void accepts() {
            // when // then
            assertThat(issue("K-issue-1")).startsWith("SUP-");
        }

        @Test
        @DisplayName("필수 값이 없으면 400 INVALID_REQUEST")
        void validates() {
            // when
            var result = mvc.post().uri("/mock/supplier/orders").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"orderNo":"ORD-20261020-AAAA1111"}
                            """);

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }
    }

    @Nested
    @DisplayName("GET /mock/supplier/profiles/{iccid} · POST .../state")
    class Profiles {

        @Test
        @DisplayName("발급된 프로필을 조회하고 상태를 ENABLED로 바꾸면 activatedAt이 채워진다")
        void find_and_change_state() {
            // given
            String supplierOrderNo = issue("K-profile-1");
            String iccid = service.findIccidBySupplierOrderNo(supplierOrderNo).orElseThrow();

            // when
            var before = mvc.get().uri("/mock/supplier/profiles/{iccid}", iccid);
            var changed = mvc.post().uri("/mock/supplier/profiles/{iccid}/state", iccid)
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            {"state":"ENABLED"}
                            """);

            // then
            assertThat(before).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("RELEASED");
            assertThat(changed).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("ENABLED");
            assertThat(mvc.get().uri("/mock/supplier/profiles/{iccid}", iccid))
                    .bodyJson().extractingPath("$.activatedAt").asString().endsWith("Z");
        }

        @Test
        @DisplayName("모르는 ICCID면 404 PROFILE_NOT_FOUND")
        void unknown() {
            // when
            var result = mvc.get().uri("/mock/supplier/profiles/8900000000000000000");

            // then
            assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson().extractingPath("$.code").isEqualTo("PROFILE_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("장애 주입 설정 API")
    class Faults {

        @Test
        @DisplayName("기본은 장애 없음, PUT으로 교체, 범위를 벗어나면 400")
        void config_round_trip() {
            // when // then
            assertThat(mvc.get().uri("/mock/supplier/config")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                    {"issueFailureRate":0.0,"callbackDelayMs":0,"callbackDuplicates":0,"callbackDrop":false,
                     "invalidSignature":false}
                    """);
            assertThat(mvc.put().uri("/mock/supplier/config").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"callbackDrop":true}
                            """)).hasStatusOk().bodyJson().extractingPath("$.callbackDrop").isEqualTo(true);
            assertThat(mvc.put().uri("/mock/supplier/config").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"issueFailureRate":2}
                            """)).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_FAULT_CONFIG");
        }
    }

    @Nested
    @DisplayName("POST /mock/supplier/usim/activate")
    class ActivateUsim {

        @Test
        @DisplayName("200과 ENABLED·개통 시각, ICCID가 없으면 400")
        void activates() {
            // when // then
            assertThat(mvc.post().uri("/mock/supplier/usim/activate").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"iccid":"8982300912345678910"}
                            """)).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("ENABLED");
            assertThat(mvc.post().uri("/mock/supplier/usim/activate").contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }
}
