package com.roampass_server.common.domain;

/**
 * 요청을 보낸 최종 사용자 식별자(X-User-Id 헤더 값)
 * 회원 도메인 없음: 과제가 헤더 수준의 식별만으로 충분하다고 명시
 */
public record UserId(String value) {

    public UserId {
        IdentifierFormat.require(value, "INVALID_USER_ID", "UserId");
    }

    public static UserId of(String value) {
        return new UserId(value);
    }
}
