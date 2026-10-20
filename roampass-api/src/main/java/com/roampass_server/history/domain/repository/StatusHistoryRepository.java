package com.roampass_server.history.domain.repository;

import java.util.List;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.StatusHistory;

/**
 * 상태 변경 이력 저장소 포트. 추가와 조회만 제공, 수정·삭제 없음
 */
public interface StatusHistoryRepository {

    void append(StatusHistory history);

    /** 주문 하나의 이력, 발생 시각 오름차순(같은 시각이면 저장 순서) */
    List<StatusHistory> findByOrderNo(OrderNo orderNo);
}
