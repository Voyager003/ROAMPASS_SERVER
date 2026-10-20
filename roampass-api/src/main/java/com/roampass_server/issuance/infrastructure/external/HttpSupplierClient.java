package com.roampass_server.issuance.infrastructure.external;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.issuance.domain.service.SupplierIssueRequest;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;

import lombok.extern.slf4j.Slf4j;

/**
 * SupplierClient의 HTTP 구현(mock-supplier 계약, 부록 A). 타임아웃은 RestClient 생성 시 지정(IssuanceConfig)
 * 공급사 응답 형식은 여기서만 알고, 밖으로는 우리 도메인 타입(SupplierIssueResult)만 내보냄
 * 4xx는 거절, 5xx·타임아웃·연결 실패는 접수 여부를 모름(같은 멱등 키로 재전송)
 */
@Slf4j
public class HttpSupplierClient implements SupplierClient {

    private final RestClient restClient;

    public HttpSupplierClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SupplierIssueResult requestIssuance(SupplierIssueRequest request) {
        try {
            IssueResponse response = restClient.post().uri("/mock/supplier/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new IssueBody(request.idempotencyKey(), request.orderNo().value()))
                    .retrieve()
                    .body(IssueResponse.class);
            if (response == null || response.supplierOrderNo() == null) {
                return SupplierIssueResult.unknown("EMPTY_RESPONSE");
            }
            return SupplierIssueResult.accepted(SupplierOrderNo.of(response.supplierOrderNo()));
        } catch (HttpClientErrorException e) {
            log.warn("Supplier rejected issuance {}: {}", request.idempotencyKey(), e.getStatusCode());
            return SupplierIssueResult.rejected("SUPPLIER_REJECTED_" + e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("Supplier acceptance unknown {}: {}", request.idempotencyKey(), e.getMessage());
            return SupplierIssueResult.unknown(e.getClass().getSimpleName());
        }
    }

    record IssueBody(String idempotencyKey, String orderNo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IssueResponse(String supplierOrderNo, String orderNo, String status) {
    }
}
