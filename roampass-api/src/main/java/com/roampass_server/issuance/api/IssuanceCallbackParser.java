package com.roampass_server.issuance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.issuance.application.IssuanceCallbackCommand;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공급사 콜백 본문(JSON) → 명령. 서명 검증 뒤에만 호출
 */
class IssuanceCallbackParser {

    private final JsonMapper jsonMapper;

    IssuanceCallbackParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    IssuanceCallbackCommand parse(String rawBody) {
        try {
            Body body = jsonMapper.readValue(rawBody, Body.class);
            return new IssuanceCallbackCommand(body.eventId(), body.eventType(), body.supplierOrderNo(),
                    body.orderNo(), body.iccid(), body.activationCode(), body.reason());
        } catch (JacksonException e) {
            throw new InvalidValueException("INVALID_CALLBACK", "Callback body is not valid JSON");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(String eventId, String eventType, String supplierOrderNo, String orderNo, String iccid,
                String activationCode, String reason) {
    }
}
