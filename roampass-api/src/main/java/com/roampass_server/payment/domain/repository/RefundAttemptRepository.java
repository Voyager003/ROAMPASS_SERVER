package com.roampass_server.payment.domain.repository;

import java.time.Instant;
import java.util.List;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.RefundAttempt;

/**
 * 환불 요청 저장소 포트
 */
public interface RefundAttemptRepository {

    /** 새 환불 요청 저장, id 부여 */
    RefundAttempt save(RefundAttempt attempt);

    /** 지금 PG 취소를 시도할 PENDING 요청(다음 시도 시각 도달), 오래된 순 */
    List<RefundAttempt> findDue(Instant now, int limit);

    List<RefundAttempt> findByOrderNo(OrderNo orderNo);

    /**
     * 저장된 요청이 아직 PENDING이고 시도 횟수가 {@code expectedAttemptCount}일 때만 변경 저장(조건부 UPDATE)
     * 같은 요청을 두 실행이 동시에 처리해도 결과는 한 번만 반영
     */
    boolean saveIfPending(RefundAttempt attempt, int expectedAttemptCount);
}
