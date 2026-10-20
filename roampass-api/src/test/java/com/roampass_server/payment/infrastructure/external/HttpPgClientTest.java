package com.roampass_server.payment.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.service.PgApproveRequest;
import com.roampass_server.payment.domain.service.PgApproveResult;
import com.roampass_server.payment.domain.service.PgApproveResult.Outcome;

@DisplayName("PG HTTP 어댑터")
class HttpPgClientTest {

    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");
    private static final PgApproveRequest REQUEST = new PgApproveRequest(IdempotencyKey.of(ORDER_NO, 1), ORDER_NO,
            Money.krw(9_900));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://pg.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpPgClient client = new HttpPgClient(builder.build());

    @Test
    @DisplayName("승인 요청 본문에 멱등 키·주문번호·금액을 담고, APPROVED 응답을 거래 ID·금액으로 변환")
    void approved() {
        // given
        server.expect(requestTo("http://pg.test/mock/pg/approve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"idempotencyKey":"ORD-20261020-AAAA1111-1","orderNo":"ORD-20261020-AAAA1111",
                         "amount":9900,"currency":"KRW"}
                        """))
                .andRespond(withSuccess("""
                        {"idempotencyKey":"ORD-20261020-AAAA1111-1","pgTid":"pg_abc","orderNo":"ORD-20261020-AAAA1111",
                         "amount":9900,"currency":"KRW","status":"APPROVED","cancelledAmount":0,
                         "approvedAt":"2026-10-20T03:00:01Z"}
                        """, MediaType.APPLICATION_JSON));

        // when
        PgApproveResult result = client.approve(REQUEST);

        // then
        assertThat(result.outcome()).isEqualTo(Outcome.APPROVED);
        assertThat(result.pgTid()).isEqualTo(PgTid.of("pg_abc"));
        assertThat(result.amount()).isEqualTo(Money.krw(9_900));
        server.verify();
    }

    @Test
    @DisplayName("FAILED 응답이면 FAILED")
    void failed() {
        // given
        server.expect(requestTo("http://pg.test/mock/pg/approve")).andRespond(withSuccess("""
                {"idempotencyKey":"ORD-20261020-AAAA1111-1","pgTid":null,"amount":9900,"currency":"KRW","status":"FAILED"}
                """, MediaType.APPLICATION_JSON));

        // when // then
        assertThat(client.approve(REQUEST).outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    @DisplayName("4xx(요청 거절)면 FAILED, 사유에 상태 코드")
    void client_error_is_failed() {
        // given
        server.expect(requestTo("http://pg.test/mock/pg/approve")).andRespond(withStatus(HttpStatus.CONFLICT));

        // when
        PgApproveResult result = client.approve(REQUEST);

        // then
        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.failureReason()).contains("409");
    }

    @Test
    @DisplayName("5xx면 UNKNOWN(PG가 처리했는지 알 수 없음)")
    void server_error_is_unknown() {
        // given
        server.expect(requestTo("http://pg.test/mock/pg/approve")).andRespond(withServerError());

        // when // then
        assertThat(client.approve(REQUEST).outcome()).isEqualTo(Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("읽기 타임아웃이면 UNKNOWN")
    void timeout_is_unknown() {
        // given
        server.expect(requestTo("http://pg.test/mock/pg/approve"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when // then
        assertThat(client.approve(REQUEST).outcome()).isEqualTo(Outcome.UNKNOWN);
    }
}
