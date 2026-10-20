package com.roampass_server.common.domain;

import com.roampass_server.common.domain.exception.InvalidValueException;

import java.util.regex.Pattern;

/**
 * 요청 헤더로 들어오는 식별자의 공통 검증
 * 상태 변경 이력과 로그에 그대로 남는 값이라 좁은 문자 집합만 허용,
 * 에러 메시지에 입력값을 넣지 않음(로그 주입 방지)
 */
final class IdentifierFormat {

    static final int MAX_LENGTH = 64;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]+");

    private IdentifierFormat() {
    }

    static String require(String value, String errorCode, String label) {
        if (value == null || value.isEmpty()) {
            throw new InvalidValueException(errorCode, label + " must not be empty");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidValueException(errorCode, label + " must be at most " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new InvalidValueException(errorCode, label + " may contain only letters, digits, '_' and '-'");
        }
        return value;
    }
}
