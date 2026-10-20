package com.roampass_server.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.model.PlanSpec;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;

@DisplayName("PlanQueryService")
class PlanQueryServiceTest {

    private static final Plan US_ESIM_7D = Plan.of(1L, PlanSpec.of("US", 7, 5_120), SimType.ESIM, Money.krw(20_000));
    private static final Plan JP_USIM_5D = Plan.of(2L, PlanSpec.of("JP", 5, 3_072), SimType.USIM, Money.krw(15_000));
    private static final Plan JP_ESIM_5D_1GB = Plan.of(3L, PlanSpec.of("JP", 5, 1_024), SimType.ESIM, Money.krw(8_000));
    private static final Plan JP_ESIM_3D = Plan.of(4L, PlanSpec.of("JP", 3, 3_072), SimType.ESIM, Money.krw(7_000));

    private PlanQueryService service;

    @BeforeEach
    void setUp() {
        FakePlanRepository repository = new FakePlanRepository()
                .add(US_ESIM_7D).add(JP_USIM_5D).add(JP_ESIM_5D_1GB).add(JP_ESIM_3D);
        service = new PlanQueryService(repository);
    }

    @Nested
    @DisplayName("요금제를 검색하면")
    class Search {

        @Test
        @DisplayName("조건이 없으면 전체를 국가 → 일수 → 용량 → id 순으로 돌려준다")
        void returns_all_sorted() {
            // when
            List<Plan> plans = service.search(PlanSearchCondition.all());

            // then
            assertThat(plans).containsExactly(JP_ESIM_3D, JP_ESIM_5D_1GB, JP_USIM_5D, US_ESIM_7D);
        }

        @Test
        @DisplayName("조건에 맞는 요금제만 돌려준다")
        void filters_by_condition() {
            // when
            List<Plan> plans = service.search(PlanSearchCondition.of("JP", SimType.ESIM, 5, null));

            // then
            assertThat(plans).containsExactly(JP_ESIM_5D_1GB);
        }

        @Test
        @DisplayName("맞는 요금제가 없으면 빈 목록")
        void empty_when_nothing_matches() {
            // when & then
            assertThat(service.search(PlanSearchCondition.of("TH", null, null, null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("요금제 하나를 조회하면")
    class GetPlan {

        @Test
        @DisplayName("있으면 돌려준다")
        void returns_existing_plan() {
            // when & then
            assertThat(service.getPlan(2L)).isEqualTo(JP_USIM_5D);
        }

        @Test
        @DisplayName("없으면 PLAN_NOT_FOUND")
        void throws_when_missing() {
            // when & then
            assertThatThrownBy(() -> service.getPlan(999L))
                    .isInstanceOf(NotFoundException.class)
                    .extracting("code").isEqualTo("PLAN_NOT_FOUND");
        }
    }
}
