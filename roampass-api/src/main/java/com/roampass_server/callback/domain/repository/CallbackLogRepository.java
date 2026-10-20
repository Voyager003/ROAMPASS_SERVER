package com.roampass_server.callback.domain.repository;

import java.util.List;
import java.util.Optional;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.common.domain.OrderNo;

/**
 * 외부 통지 기록 저장소 포트
 */
public interface CallbackLogRepository {

    /**
     * eventKey가 처음이면 저장(선 INSERT로 중복 판정)
     *
     * @return 저장 여부. false면 이미 받은 통지
     */
    boolean insertIfAbsent(CallbackLog log);

    /** eventKey 없는 기록(서명 불일치 등) 저장 */
    void save(CallbackLog log);

    void updateOutcome(String eventKey, CallbackOutcome outcome, String note);

    Optional<CallbackLog> findByEventKey(String eventKey);

    /** 주문의 통지 기록, 수신 시각 오름차순 */
    List<CallbackLog> findByOrderNo(OrderNo orderNo);
}
