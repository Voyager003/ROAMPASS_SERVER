package com.roampass_server.common.application.exception;

/**
 * 외부 시스템(PG·공급사)이 응답하지 않거나 실패해 요청을 끝내지 못함. 웹 계층이 503으로 변환
 */
public class UpstreamUnavailableException extends RuntimeException {

    private final String code;

    public UpstreamUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
