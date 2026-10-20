package com.roampass_server.payment.domain.service;

import com.roampass_server.common.domain.Money;
import com.roampass_server.payment.domain.model.PgTid;

/**
 * PG 취소 요청. cancelKey는 환불 요청마다 고정(재시도해도 PG가 한 번만 취소, decisions #47)
 */
public record PgCancelRequest(PgTid pgTid, String cancelKey, Money amount) {
}
