package com.roampass_server.common.application.exception;

/**
 * 현재 상태에서 받아들일 수 없는 요청(예: 결제할 수 없는 주문, 진행 중인 결제). 웹 계층이 409로 변환
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
