package com.roampass_server.issuance.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;

/**
 * 테스트용 인메모리 SimProfileRepository. 주문번호·ICCID 중복은 실제 유니크 제약처럼 거부
 * 폴링 대상 판정에 필요한 여행 시작일은 주문 조회 함수로 얻음
 */
public class FakeSimProfileRepository implements SimProfileRepository {

    private static final EnumSet<InstallStatus> NOT_ACTIVATED =
            EnumSet.of(InstallStatus.ISSUED, InstallStatus.QR_VIEWED, InstallStatus.INSTALLED);

    private final List<SimProfile> profiles = new ArrayList<>();
    private final Function<OrderNo, LocalDate> travelStartDateOf;

    public FakeSimProfileRepository(Function<OrderNo, LocalDate> travelStartDateOf) {
        this.travelStartDateOf = travelStartDateOf;
    }

    @Override
    public synchronized SimProfile save(SimProfile profile) {
        boolean duplicate = profiles.stream().anyMatch(p -> p.orderNo().equals(profile.orderNo())
                || p.iccid().equals(profile.iccid()));
        if (duplicate) {
            throw new IllegalStateException("duplicate sim profile");
        }
        profiles.add(profile);
        return profile;
    }

    @Override
    public synchronized Optional<SimProfile> findByOrderNo(OrderNo orderNo) {
        return profiles.stream().filter(p -> p.orderNo().equals(orderNo)).findFirst();
    }

    @Override
    public synchronized List<SimProfile> findTrackable(LocalDate today, int lookaheadDays, int limit) {
        return profiles.stream()
                .filter(p -> NOT_ACTIVATED.contains(p.status()) && p.deletedAt() == null)
                .filter(p -> {
                    LocalDate start = travelStartDateOf.apply(p.orderNo());
                    return !start.isAfter(today.plusDays(lookaheadDays))
                            && !start.plusDays(p.usageDays()).isBefore(today);
                })
                .sorted(Comparator.comparing(SimProfile::issuedAt))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized boolean saveIfStatus(SimProfile profile, InstallStatus expected) {
        for (int i = 0; i < profiles.size(); i++) {
            SimProfile current = profiles.get(i);
            if (current.orderNo().equals(profile.orderNo())) {
                if (current.status() != expected) {
                    return false;
                }
                profiles.set(i, profile);
                return true;
            }
        }
        return false;
    }

    public synchronized int count() {
        return profiles.size();
    }
}
