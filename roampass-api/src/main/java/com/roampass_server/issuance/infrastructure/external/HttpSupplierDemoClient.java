package com.roampass_server.issuance.infrastructure.external;

import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.logging.LogValues;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.service.SupplierDemoClient;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;

import lombok.extern.slf4j.Slf4j;

/** 모의 공급사 시연용 API 호출(`/mock/supplier/profiles/{iccid}/state`, `/mock/supplier/usim/cards`) */
@Slf4j
public class HttpSupplierDemoClient implements SupplierDemoClient {

    private final RestClient restClient;

    public HttpSupplierDemoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public SupplierProfileResult changeProfileState(Iccid iccid, SupplierProfileState state) {
        long startedAt = System.nanoTime();
        SupplierProfileResult result;
        try {
            ProfileResponse response = restClient.post().uri("/mock/supplier/profiles/{iccid}/state", iccid.value())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new StateBody(state.name()))
                    .retrieve()
                    .body(ProfileResponse.class);
            result = response == null || response.state() == null
                    ? SupplierProfileResult.unknown()
                    : SupplierProfileResult.found(SupplierProfileState.valueOf(response.state()), response.activatedAt(),
                            response.usedMb());
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("event=supplier_call_error op=demo_state iccid={} error=\"{}\"", LogValues.iccid(iccid.value()),
                    e.getMessage());
            result = SupplierProfileResult.unknown();
        }
        log.info("event=supplier_call op=demo_state iccid={} requested={} outcome={} elapsedMs={}",
                LogValues.iccid(iccid.value()), state, result.outcome(), LogValues.elapsedMs(startedAt));
        return result;
    }

    @Override
    public Optional<String> issuePickupCard(OrderNo orderNo) {
        long startedAt = System.nanoTime();
        Optional<String> iccid;
        try {
            CardResponse response = restClient.post().uri("/mock/supplier/usim/cards")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new CardBody(orderNo.value()))
                    .retrieve()
                    .body(CardResponse.class);
            iccid = Optional.ofNullable(response).map(CardResponse::iccid);
        } catch (RestClientException e) {
            log.warn("event=supplier_call_error op=demo_pickup_card orderNo={} error=\"{}\"", orderNo.value(),
                    e.getMessage());
            iccid = Optional.empty();
        }
        log.info("event=supplier_call op=demo_pickup_card orderNo={} iccid={} elapsedMs={}", orderNo.value(),
                LogValues.iccid(iccid.orElse(null)), LogValues.elapsedMs(startedAt));
        return iccid;
    }

    record StateBody(String state) {
    }

    record CardBody(String orderNo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CardResponse(String orderNo, String iccid) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfileResponse(String iccid, String state, java.time.Instant activatedAt, long usedMb) {
    }
}
