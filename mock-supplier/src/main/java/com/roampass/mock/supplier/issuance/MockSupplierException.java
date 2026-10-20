package com.roampass.mock.supplier.issuance;

import org.springframework.http.HttpStatus;

/**
 * mock-supplier 처리 거절. HTTP 상태와 code를 함께 보관
 */
public class MockSupplierException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MockSupplierException(HttpStatus status, String code, String message) {
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
