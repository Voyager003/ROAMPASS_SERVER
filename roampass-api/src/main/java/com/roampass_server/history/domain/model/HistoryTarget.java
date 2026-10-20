package com.roampass_server.history.domain.model;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

/**
 * 이력 기록이 가리키는 집합 루트: 종류와 그 대상의 키
 * (주문번호, 결제 멱등 키, 환불 id 등). history 도메인은 다른 도메인의 타입을 import하지 않으므로
 * 키는 일반 문자열
 */
public record HistoryTarget(TargetType type, String key) {

    public HistoryTarget {
        if (type == null || key == null || key.isBlank()) {
            throw new InvalidValueException("INVALID_HISTORY", "History target needs a type and a key");
        }
    }

    public static HistoryTarget of(TargetType type, String key) {
        return new HistoryTarget(type, key);
    }

    public static HistoryTarget order(OrderNo orderNo) {
        return new HistoryTarget(TargetType.ORDER, orderNo.value());
    }
}
