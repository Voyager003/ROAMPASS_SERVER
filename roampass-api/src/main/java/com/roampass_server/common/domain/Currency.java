package com.roampass_server.common.domain;

/**
 * 서비스가 가격을 매길 수 있는 통화. 금액은 통화의 최소 단위로 저장
 * ({@link #fractionDigits()} = 0이면 원처럼 기본 단위 그대로)
 * 현재 판매는 KRW만 사용, USD는 {@link Money}의 통화 검사를 실제로 테스트하기 위한 값
 * 환율과 통화 표시는 가산점 다통화(B-1) 범위
 */
public enum Currency {
    KRW(0),
    USD(2);

    private final int fractionDigits;

    Currency(int fractionDigits) {
        this.fractionDigits = fractionDigits;
    }

    public int fractionDigits() {
        return fractionDigits;
    }
}
