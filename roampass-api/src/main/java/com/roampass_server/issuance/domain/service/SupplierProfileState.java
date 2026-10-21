package com.roampass_server.issuance.domain.service;

/**
 * 공급사가 알려 주는 프로필 상태(우리 도메인 이름). RELEASED: 내려받을 수 있음(미설치),
 * INSTALLED: 기기에 설치, ENABLED: 개통(사용 시작), DELETED: 기기에서 삭제
 */
public enum SupplierProfileState {
    RELEASED,
    INSTALLED,
    ENABLED,
    DELETED
}
