package com.roampass_server.issuance.domain.repository;

import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.SimProfile;

/**
 * SIM 프로필 저장소 포트. 주문당 1개·ICCID당 1개는 DB 유니크 제약으로도 보장
 */
public interface SimProfileRepository {

    SimProfile save(SimProfile profile);

    Optional<SimProfile> findByOrderNo(OrderNo orderNo);
}
