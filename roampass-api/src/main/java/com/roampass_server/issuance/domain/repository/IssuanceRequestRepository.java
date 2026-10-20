package com.roampass_server.issuance.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.IssuanceStatus;

/**
 * 발급 요청 저장소 포트
 */
public interface IssuanceRequestRepository {

    IssuanceRequest save(IssuanceRequest request);

    /** 주문의 가장 최근 발급 요청 */
    Optional<IssuanceRequest> findLatestByOrderNo(OrderNo orderNo);

    int countByOrderNo(OrderNo orderNo);

    /** 공급사 접수 응답을 아직 못 받은 요청(재전송 대상), 오래된 순 */
    List<IssuanceRequest> findPending(int limit);

    /** {@code requestedBefore} 이전에 요청되어 아직 결과가 없는 요청(PENDING·ACCEPTED), 오래된 순 */
    List<IssuanceRequest> findOpenRequestedBefore(Instant requestedBefore, int limit);

    /** 저장된 상태가 아직 {@code expected}일 때만 변경 저장(조건부 UPDATE) */
    boolean saveIfStatus(IssuanceRequest request, IssuanceStatus expected);
}
