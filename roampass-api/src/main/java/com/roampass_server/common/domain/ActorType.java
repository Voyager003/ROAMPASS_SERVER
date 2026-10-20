package com.roampass_server.common.domain;

/**
 * 변경을 일으킨 주체(또는 신호)
 */
public enum ActorType {
    /** 최종 사용자 (X-User-Id) */
    USER,
    /** 운영자 (X-Admin-Id) */
    ADMIN,
    /** 우리 서버의 스케줄러·자동 처리 */
    SYSTEM,
    /** 결제 대행사 (승인 응답, 웹훅) */
    PG,
    /** SIM 공급사 (콜백, 상태 조회) */
    SUPPLIER
}
