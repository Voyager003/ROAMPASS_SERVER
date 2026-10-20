package com.roampass_server.common.web;

/**
 * 필수 식별 헤더 누락. {@link GlobalExceptionHandler}가 401로 변환
 * "헤더가 없다"는 도메인 규칙이 아닌 HTTP 관심사라서 웹 계층에 위치
 */
public class MissingAuthHeaderException extends RuntimeException {

    private final String code;

    public MissingAuthHeaderException(String code, String header) {
        super(header + " header is required");
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
