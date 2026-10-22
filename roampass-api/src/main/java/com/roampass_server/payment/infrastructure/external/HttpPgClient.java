package com.roampass_server.payment.infrastructure.external;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.logging.LogValues;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.service.PgApproveRequest;
import com.roampass_server.payment.domain.service.PgApproveResult;
import com.roampass_server.payment.domain.service.PgCancelRequest;
import com.roampass_server.payment.domain.service.PgCancelResult;
import com.roampass_server.payment.domain.service.PgClient;
import com.roampass_server.payment.domain.service.PgInquiryResult;

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
        long startedAt = System.nanoTime();
        PgApproveResult result = doApprove(request);
        log.info("event=pg_call op=approve idempotencyKey={} amount={} outcome={} reason={} elapsedMs={}",
                request.idempotencyKey().value(), request.amount().amount(), result.outcome(),
                result.failureReason() == null ? "-" : result.failureReason(), LogValues.elapsedMs(startedAt));
        return result;
    }

    private PgApproveResult doApprove(PgApproveRequest request) {
        try {
            ApproveResponse response = restClient.post().uri("/mock/pg/approve")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApproveBody(request.idempotencyKey().value(), request.orderNo().value(),
                            request.amount().amount(), request.amount().currency().name()))
                    .retrieve()
                    .body(ApproveResponse.class);
            return toResult(response);
        } catch (HttpClientErrorException e) {
            log.warn("event=pg_call_error op=approve idempotencyKey={} status={}", request.idempotencyKey().value(),
                    e.getStatusCode().value());
            return PgApproveResult.failed("PG_REJECTED_" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("event=pg_call_error op=approve idempotencyKey={} error=\"{}\"", request.idempotencyKey().value(),
                    e.getMessage());
            return PgApproveResult.unknown(e.getClass().getSimpleName());
        }
    }

    @Override
    public PgInquiryResult inquire(IdempotencyKey key) {
        long startedAt = System.nanoTime();
        PgInquiryResult result = doInquire(key);
        log.info("event=pg_call op=inquiry idempotencyKey={} status={} elapsedMs={}", key.value(), result.status(),
                LogValues.elapsedMs(startedAt));
        return result;
    }

    private PgInquiryResult doInquire(IdempotencyKey key) {
        try {
            ApproveResponse response = restClient.get().uri("/mock/pg/payments/{key}", key.value())
                    .retrieve()
                    .body(ApproveResponse.class);
            return toInquiryResult(response);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return PgInquiryResult.notFound();
            }
            log.warn("event=pg_call_error op=inquiry idempotencyKey={} status={}", key.value(), e.getStatusCode().value());
            return PgInquiryResult.unknown();
        } catch (RestClientException e) {
            log.warn("event=pg_call_error op=inquiry idempotencyKey={} error=\"{}\"", key.value(), e.getMessage());
            return PgInquiryResult.unknown();
        }
    }

    @Override
    public PgCancelResult cancel(PgCancelRequest request) {
        long startedAt = System.nanoTime();
        PgCancelResult result = doCancel(request);
        log.info("event=pg_call op=cancel cancelKey={} pgTid={} amount={} outcome={} reason={} elapsedMs={}",
                request.cancelKey(), request.pgTid().value(), request.amount().amount(), result.outcome(),
                result.reason() == null ? "-" : result.reason(), LogValues.elapsedMs(startedAt));
        return result;
    }

    private PgCancelResult doCancel(PgCancelRequest request) {
        try {
            restClient.post().uri("/mock/pg/cancel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CancelBody(request.pgTid().value(), request.cancelKey(), request.amount().amount()))
                    .retrieve()
                    .toBodilessEntity();
            return PgCancelResult.cancelled();
        } catch (HttpClientErrorException e) {
            log.warn("event=pg_call_error op=cancel cancelKey={} status={}", request.cancelKey(), e.getStatusCode().value());
            return PgCancelResult.failed("PG_REJECTED_" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("event=pg_call_error op=cancel cancelKey={} error=\"{}\"", request.cancelKey(), e.getMessage());
            return PgCancelResult.unknown(e.getClass().getSimpleName());
        }
    }

    private static PgInquiryResult toInquiryResult(ApproveResponse response) {
        if (response == null || response.status() == null) {
            return PgInquiryResult.unknown();
        }
        return switch (response.status()) {
            // 취소 여부와 무관하게 한 번 승인된 결제(취소는 별도 흐름)
            case "APPROVED", "PARTIALLY_CANCELLED", "CANCELLED" -> PgInquiryResult.approved(
                    PgTid.of(response.pgTid()), Money.of(response.amount(), Currency.valueOf(response.currency())));
            case "FAILED" -> PgInquiryResult.failed();
            default -> PgInquiryResult.unknown();
        };
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

    record CancelBody(String pgTid, String cancelKey, long amount) {
    }

    record ApproveBody(String idempotencyKey, String orderNo, long amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApproveResponse(String idempotencyKey, String pgTid, String status, long amount, String currency) {
    }
}
