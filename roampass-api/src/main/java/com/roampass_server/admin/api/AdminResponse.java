package com.roampass_server.admin.api;

import java.time.Instant;

/**
 * 어드민 응답 공통 봉투. asOf = 서버가 이 데이터를 조회한 시각(UTC)
 * 운영자 화면은 이 값으로 "몇 초 전 데이터인지"를 표시
 */
public record AdminResponse<T>(Instant asOf, T data) {
}
