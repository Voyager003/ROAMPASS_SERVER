package com.roampass_server.callback.application;

/**
 * 통지 서명 불일치. 웹 계층이 401로 변환
 */
public class InvalidSignatureException extends RuntimeException {

    public static final String CODE = "INVALID_SIGNATURE";

    public InvalidSignatureException() {
        super("Callback signature is invalid");
    }
}
