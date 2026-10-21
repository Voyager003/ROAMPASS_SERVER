package com.roampass_server.issuance.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 유심 ICCID 등록 요청. 형식·체크 디지트 검증은 도메인(INVALID_ICCID)
 */
public record UsimRegisterRequest(@NotBlank String iccid) {
}
