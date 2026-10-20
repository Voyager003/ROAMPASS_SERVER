package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.LocalDate;

import com.roampass_server.common.domain.KoreaTime;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;

/**
 * 설치·개통 추적 폴링. 스케줄러가 주기적으로 호출, 트랜잭션 없음(공급사 조회는 밖)
 * 공급사는 설치·개통을 콜백으로 알려 주지 않으므로(부록 A) 상태 조회가 유일한 신호
 * 대상: 개통 전 SIM 중 여행 직전(시작일 N일 전)부터 사용 기간 끝까지만. 여행이 먼 SIM·개통된 SIM은 조회하지 않음
 */
public class ProfileTrackingService {

    private final SimProfileRepository profileRepository;
    private final SupplierClient supplierClient;
    private final ProfileSignalApplier applier;
    private final Clock clock;
    private final int lookaheadDays;
    private final int batchSize;

    public ProfileTrackingService(SimProfileRepository profileRepository, SupplierClient supplierClient,
                                  ProfileSignalApplier applier, Clock clock, int lookaheadDays, int batchSize) {
        this.profileRepository = profileRepository;
        this.supplierClient = supplierClient;
        this.applier = applier;
        this.clock = clock;
        this.lookaheadDays = lookaheadDays;
        this.batchSize = batchSize;
    }

    /** @return 상태가 바뀐 프로필 수 */
    public int poll() {
        LocalDate todayKst = KoreaTime.toDate(clock.instant());
        int changed = 0;
        for (SimProfile profile : profileRepository.findTrackable(todayKst, lookaheadDays, batchSize)) {
            if (applier.apply(profile, supplierClient.getProfile(profile.iccid()))) {
                changed++;
            }
        }
        return changed;
    }
}
