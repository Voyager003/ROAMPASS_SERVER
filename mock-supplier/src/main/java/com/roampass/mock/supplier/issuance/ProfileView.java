package com.roampass.mock.supplier.issuance;

import java.time.Instant;

/**
 * 프로필 상태 조회 응답(부록 A `GET /profiles/{iccid}`)
 */
public record ProfileView(String iccid, ProfileState state, Instant activatedAt, long usedMb) {
}
