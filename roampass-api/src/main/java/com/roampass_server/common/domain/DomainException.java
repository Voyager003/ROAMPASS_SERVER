package com.roampass_server.common.domain;

/**
 * 도메인 객체가 던지는 비즈니스 규칙 위반의 기반 클래스
 * 의도적으로 순수 Java: 고정된 에러 {@code code}만 보유, HTTP 상태는 없음
 * 하위 타입별 HTTP 응답 매핑은 웹 계층 책임
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
