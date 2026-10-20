package com.roampass_server.common.domain.exception;

/**
 * 값 객체·도메인 입력의 검증 실패(예: 형식이 틀린 식별자, 음수 금액)
 */
public class InvalidValueException extends DomainException {

    public InvalidValueException(String code, String message) {
        super(code, message);
    }
}
