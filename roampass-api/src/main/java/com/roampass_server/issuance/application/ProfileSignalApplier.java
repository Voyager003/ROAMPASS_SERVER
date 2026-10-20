package com.roampass_server.issuance.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;

/**
 * 공급사 프로필 상태를 우리 설치 상태에 반영(tx). 설치 상태는 앞으로만 진행(도메인 규칙)
 * ENABLED의 개통 시각은 공급사 값(UTC) 그대로 사용 → 만료일 = 개통 + 24시간 × 사용 일수
 */
public class ProfileSignalApplier {

    private final SimProfileRepository profileRepository;
    private final StatusHistoryRecorder historyRecorder;
    private final Clock clock;

    public ProfileSignalApplier(SimProfileRepository profileRepository, StatusHistoryRecorder historyRecorder,
                                Clock clock) {
        this.profileRepository = profileRepository;
        this.historyRecorder = historyRecorder;
        this.clock = clock;
    }

    /** @return 반영 여부(변화 없음·그 사이 다른 변경이면 false) */
    @Transactional
    public boolean apply(SimProfile profile, SupplierProfileResult result) {
        if (result.outcome() != SupplierProfileResult.Outcome.FOUND) {
            return false;
        }
        Instant now = Instant.now(clock);
        SimProfile next = switch (result.state()) {
            case RELEASED -> profile;
            case INSTALLED -> profile.markInstalled(now);
            case ENABLED -> profile.markActivated(result.activatedAt() != null ? result.activatedAt() : now);
            case DELETED -> profile.markDeleted(now);
        };
        if (next == profile || !profileRepository.saveIfStatus(next, profile.status())) {
            return false;
        }
        if (next.status() != profile.status()) {
            historyRecorder.record(profile.orderNo(), HistoryTarget.of(TargetType.SIM_PROFILE, profile.iccid().value()),
                    profile.status(), next.status(), Actor.supplier(), "공급사 상태 조회: " + result.state(), now);
        }
        return true;
    }
}
