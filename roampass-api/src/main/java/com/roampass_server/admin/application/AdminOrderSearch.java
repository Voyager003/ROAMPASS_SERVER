package com.roampass_server.admin.application;

import java.time.Instant;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;

/**
 * 어드민 주문 검색 포트. 주문·결제·SIM 프로필 테이블을 함께 보는 조회라 주문 저장소가 아닌 어드민 계층에 둠
 * (도메인끼리 서로를 모르게 하기 위함, decisions #40)
 */
public interface AdminOrderSearch {

    /**
     * 조건에 맞는 주문번호, 생성 시각 내림차순(최신 먼저)
     *
     * @param now 설치 상태 EXPIRED/ACTIVE 판정 기준 시각
     */
    PageResult<OrderNo> search(AdminOrderSearchCondition condition, Instant now, int page, int size);
}
