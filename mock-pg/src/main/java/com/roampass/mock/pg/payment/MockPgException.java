package com.roampass.mock.pg.payment;

import org.springframework.http.HttpStatus;

/**
 * mock-pg 처리 거절. HTTP 상태와 code를 함께 보관
 */
public class MockPgException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MockPgException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
