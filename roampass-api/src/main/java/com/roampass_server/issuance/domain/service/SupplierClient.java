package com.roampass_server.issuance.domain.service;

import com.roampass_server.issuance.domain.model.Iccid;

/**
 * 공급사 연동 포트. 응답은 공급사 형식이 아닌 우리 도메인 타입(공급사 교체·추가 대비)
 * 네트워크 오류는 예외 대신 {@link SupplierIssueResult.Outcome#UNKNOWN}
 */
public interface SupplierClient {

    SupplierIssueResult requestIssuance(SupplierIssueRequest request);

    /** 프로필의 현재 상태(설치·개통·삭제) 조회, 설치 추적 폴링용 */
    SupplierProfileResult getProfile(Iccid iccid);

    /** 유심 개통 요청. 결과는 프로필 조회와 같은 형식(ENABLED + 개통 시각), 실패·응답 유실은 UNKNOWN */
    SupplierProfileResult activateUsim(Iccid iccid);
}
