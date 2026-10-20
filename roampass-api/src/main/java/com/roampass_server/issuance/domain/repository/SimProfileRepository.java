package com.roampass_server.issuance.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;

/**
 * SIM 프로필 저장소 포트. 주문당 1개·ICCID당 1개는 DB 유니크 제약으로도 보장
 */
public interface SimProfileRepository {

    SimProfile save(SimProfile profile);

    Optional<SimProfile> findByOrderNo(OrderNo orderNo);

    boolean existsByIccid(Iccid iccid);

    /**
     * 설치 추적 폴링 대상: 아직 개통 전(ISSUED·QR_VIEWED·INSTALLED)이고 삭제되지 않았으며,
     * 여행 시작일이 {@code today + lookaheadDays} 이전이고 시작일 + 사용 일수가 {@code today} 이후인 프로필
     * (여행 직전부터 사용 기간 끝까지만 조회, 개통되면 대상에서 빠짐)
     */
    List<SimProfile> findTrackable(LocalDate today, int lookaheadDays, int limit);

    /** 저장된 설치 상태가 아직 {@code expected}일 때만 변경 저장(조건부 UPDATE) */
    boolean saveIfStatus(SimProfile profile, InstallStatus expected);
}
