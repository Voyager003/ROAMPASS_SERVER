package com.roampass_server.common.application.exception;

/**
 * 조회한 대상이 없음(또는 요청자에게 보이지 않음)
 * 도메인 규칙 위반이 아닌 조회 결과라서 application 계층에 위치, 웹 계층이 404로 변환
 */
public class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
