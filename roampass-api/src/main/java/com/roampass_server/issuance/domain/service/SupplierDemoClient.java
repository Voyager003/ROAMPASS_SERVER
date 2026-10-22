package com.roampass_server.issuance.domain.service;

import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.Iccid;

/**
 * 모의 공급사의 시연용 기능(과제 부록 A "상태를 INSTALLED / ENABLED 로 바꾸는 엔드포인트").
 * 실제 공급사에는 없는 기능이라 운영용 {@link SupplierClient}와 분리
 */
public interface SupplierDemoClient {

    /** 공급사 쪽 프로필 상태를 바꿈(설치·개통 흉내). 실패·응답 유실은 UNKNOWN */
    SupplierProfileResult changeProfileState(Iccid iccid, SupplierProfileState state);

    /** 이 주문에 내줄 유심 수령 카드의 ICCID(실물 카드 대신). 실패하면 비어 있음 */
    Optional<String> issuePickupCard(OrderNo orderNo);
}
