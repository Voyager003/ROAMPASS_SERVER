package com.roampass_server.payment.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * 승인 시 PG가 부여하는 거래 ID, 취소와 상태 조회에 필요
 * 형식은 PG가 정하므로 "비어 있지 않음, 공백 없음, 적당한 길이"만 검사
 */
public record PgTid(String value) {

    private static final Pattern NO_WHITESPACE = Pattern.compile("\\S{1,100}");

    public PgTid {
        if (value == null || !NO_WHITESPACE.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_PG_TID", "PG transaction id must be 1-100 non-whitespace characters");
        }
    }

    public static PgTid of(String value) {
        return new PgTid(value);
    }
}
