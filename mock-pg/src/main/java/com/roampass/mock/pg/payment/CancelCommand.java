package com.roampass.mock.pg.payment;

/**
 * 취소 요청. 같은 취소 키 재요청은 한 번만 반영(호출 측 타임아웃 후 재전송 대비)
 */
public record CancelCommand(String pgTid, String cancelKey, long amount) {
}
