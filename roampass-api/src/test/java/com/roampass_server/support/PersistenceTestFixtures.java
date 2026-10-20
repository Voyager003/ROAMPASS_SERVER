package com.roampass_server.support;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.PlanSnapshot;

/**
 * 영속성 통합 테스트 공용 픽스처. 시드 요금제 id 1(JP 이심 5일)을 참조
 */
public final class PersistenceTestFixtures {

    // 2026-10-20 12:00 KST
    public static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    public static final Duration WINDOW = Duration.ofMinutes(10);
    public static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 10, 25);
    public static final UserId USER = UserId.of("user-1");
    public static final PlanSnapshot SEED_PLAN_1 = PlanSnapshot.of(1L, SimType.ESIM, Money.krw(9_900));

    private PersistenceTestFixtures() {
    }

    public static Order pendingOrder(String suffix, Instant createdAt) {
        return Order.create(OrderNo.of("ORD-20261020-" + suffix), USER, SEED_PLAN_1, TRAVEL_DATE, createdAt, WINDOW);
    }
}
