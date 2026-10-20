package com.roampass_server.issuance.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;

/**
 * 테스트용 인메모리 SimProfileRepository. 주문번호·ICCID 중복은 실제 유니크 제약처럼 거부
 */
public class FakeSimProfileRepository implements SimProfileRepository {

    private final List<SimProfile> profiles = new ArrayList<>();

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

    public synchronized int count() {
        return profiles.size();
    }
}
