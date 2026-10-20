package com.roampass_server.issuance.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierIssueRequest;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;
import com.roampass_server.issuance.domain.service.SupplierIssueResult.Outcome;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;

@DisplayName("공급사 HTTP 어댑터")
class HttpSupplierClientTest {

    private static final SupplierIssueRequest REQUEST = new SupplierIssueRequest("ORD-20261021-AAAA1111-ISSUE-1",
            OrderNo.of("ORD-20261021-AAAA1111"));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://supplier.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpSupplierClient client = new HttpSupplierClient(builder.build());

    @Test
    @DisplayName("멱등 키·주문번호를 보내고 202 응답의 접수 번호로 ACCEPTED")
    void accepted() {
        // given
        server.expect(requestTo("http://supplier.test/mock/supplier/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"idempotencyKey":"ORD-20261021-AAAA1111-ISSUE-1","orderNo":"ORD-20261021-AAAA1111"}
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"supplierOrderNo":"SUP-abc","orderNo":"ORD-20261021-AAAA1111","status":"ACCEPTED"}
                        """));

        // when
        SupplierIssueResult result = client.requestIssuance(REQUEST);

        // then
        assertThat(result.outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(result.supplierOrderNo()).isEqualTo(SupplierOrderNo.of("SUP-abc"));
        server.verify();
    }

    @Test
    @DisplayName("4xx면 REJECTED, 5xx·타임아웃이면 UNKNOWN")
    void errors() {
        // given
        server.expect(requestTo("http://supplier.test/mock/supplier/orders")).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        server.expect(requestTo("http://supplier.test/mock/supplier/orders")).andRespond(withServerError());
        server.expect(requestTo("http://supplier.test/mock/supplier/orders"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when // then
        assertThat(client.requestIssuance(REQUEST).outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(client.requestIssuance(REQUEST).outcome()).isEqualTo(Outcome.UNKNOWN);
        assertThat(client.requestIssuance(REQUEST).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("프로필 조회: 상태·개통 시각을 도메인 타입으로, 404면 NOT_FOUND, 5xx면 UNKNOWN")
    void get_profile() {
        // given
        Iccid iccid = Iccid.of("8982300912345678902");
        server.expect(requestTo("http://supplier.test/mock/supplier/profiles/8982300912345678902"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"iccid":"8982300912345678902","state":"ENABLED","activatedAt":"2026-05-01T15:20:00Z","usedMb":12}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://supplier.test/mock/supplier/profiles/8982300912345678902"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://supplier.test/mock/supplier/profiles/8982300912345678902"))
                .andRespond(withServerError());

        // when
        SupplierProfileResult found = client.getProfile(iccid);

        // then
        assertThat(found.state()).isEqualTo(SupplierProfileState.ENABLED);
        assertThat(found.activatedAt()).isEqualTo(Instant.parse("2026-05-01T15:20:00Z"));
        assertThat(found.usedMb()).isEqualTo(12);
        assertThat(client.getProfile(iccid).outcome()).isEqualTo(SupplierProfileResult.Outcome.NOT_FOUND);
        assertThat(client.getProfile(iccid).outcome()).isEqualTo(SupplierProfileResult.Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("유심 개통: ICCID를 보내고 ENABLED·개통 시각을 받음, 5xx면 UNKNOWN")
    void activate_usim() {
        // given
        Iccid iccid = Iccid.of("8982300912345678902");
        server.expect(requestTo("http://supplier.test/mock/supplier/usim/activate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"iccid":"8982300912345678902"}
                        """))
                .andRespond(withSuccess("""
                        {"iccid":"8982300912345678902","state":"ENABLED","activatedAt":"2026-10-21T00:00:05Z","usedMb":0}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://supplier.test/mock/supplier/usim/activate")).andRespond(withServerError());

        // when // then
        assertThat(client.activateUsim(iccid).state()).isEqualTo(SupplierProfileState.ENABLED);
        assertThat(client.activateUsim(iccid).outcome()).isEqualTo(SupplierProfileResult.Outcome.UNKNOWN);
    }
}
