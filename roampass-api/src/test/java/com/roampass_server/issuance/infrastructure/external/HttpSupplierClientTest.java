package com.roampass_server.issuance.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierIssueRequest;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;
import com.roampass_server.issuance.domain.service.SupplierIssueResult.Outcome;

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
}
