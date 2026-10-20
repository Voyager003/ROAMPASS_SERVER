package com.roampass_server.common.domain;

import java.util.regex.Pattern;

/**
 * PG·공급사·사용자와 함께 쓰는, 외부에 보이는 주문번호
 * 주문·결제·발급을 잇는 시스템 간 연결 키라서 공유 커널에 위치
 * 형식: {@code ORD-{KST yyyyMMdd}-{영문 대문자·숫자 8자}} — 날짜는 문의 대응용,
 * 랜덤 부분은 다른 사람의 주문번호 추측 방지
 */
public record OrderNo(String value) {

    private static final Pattern FORMAT = Pattern.compile("ORD-\\d{8}-[A-Z0-9]{8}");

    public OrderNo {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_ORDER_NO", "Order number must look like ORD-yyyyMMdd-XXXXXXXX");
        }
    }

    public static OrderNo of(String value) {
        return new OrderNo(value);
    }
}
