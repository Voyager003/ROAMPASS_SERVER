package com.roampass_server.payment.infrastructure.external;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.service.PgApproveRequest;
import com.roampass_server.payment.domain.service.PgApproveResult;
import com.roampass_server.payment.domain.service.PgClient;

import lombok.extern.slf4j.Slf4j;

/**
 * PgClient의 HTTP 구현(mock-pg 계약). 타임아웃은 RestClient 생성 시 지정(PaymentConfig)
 * 결과 판정: 2xx 본문의 status로 승인/실패, 4xx는 PG가 요청을 거절했으므로 실패,
 * 5xx·타임아웃·연결 실패는 PG가 처리했는지 알 수 없으므로 UNKNOWN
 */
@Slf4j
public class HttpPgClient implements PgClient {

    private final RestClient restClient;

    public HttpPgClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PgApproveResult approve(PgApproveRequest request) {
        try {
            ApproveResponse response = restClient.post().uri("/mock/pg/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApproveBody(request.idempotencyKey().value(), request.orderNo().value(),
                            request.amount().amount(), request.amount().currency().name()))
                    .retrieve()
                    .body(ApproveResponse.class);
            return toResult(response);
        } catch (HttpClientErrorException e) {
            log.warn("PG rejected approve {}: {}", request.idempotencyKey().value(), e.getStatusCode());
            return PgApproveResult.failed("PG_REJECTED_" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("PG approve result unknown {}: {}", request.idempotencyKey().value(), e.getMessage());
            return PgApproveResult.unknown(e.getClass().getSimpleName());
        }
    }

    private static PgApproveResult toResult(ApproveResponse response) {
        if (response == null || response.status() == null) {
            return PgApproveResult.unknown("EMPTY_RESPONSE");
        }
        return switch (response.status()) {
            case "APPROVED" -> PgApproveResult.approved(PgTid.of(response.pgTid()),
                    Money.of(response.amount(), Currency.valueOf(response.currency())));
            case "FAILED" -> PgApproveResult.failed("PG_DECLINED");
            default -> PgApproveResult.unknown("UNEXPECTED_STATUS_" + response.status());
        };
    }

    record ApproveBody(String idempotencyKey, String orderNo, long amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApproveResponse(String idempotencyKey, String pgTid, String status, long amount, String currency) {
    }
}
