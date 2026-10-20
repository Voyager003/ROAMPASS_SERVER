package com.roampass_server.plan.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.repository.PlanRepository;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;
import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@Transactional
@DisplayName("PlanRepository (PostgreSQL + Flyway 시드)")
class PlanRepositoryAdapterIntegrationTest {

    @Autowired
    private PlanRepository planRepository;

    @Nested
    @DisplayName("시드 요금제는")
    class SeedData {

        @Test
        @DisplayName("4개 국가 × 이심/유심 × 2개 상품 = 16개다")
        void has_sixteen_plans() {
            // when & then
            assertThat(planRepository.search(PlanSearchCondition.all())).hasSize(16);
        }

        @Test
        @DisplayName("id 1은 일본 이심 5일 3GB 9,900원이다")
        void plan_one_is_japan_esim() {
            // when
            Plan plan = planRepository.findById(1L).orElseThrow();

            // then
            assertThat(plan.spec().countryCode()).isEqualTo("JP");
            assertThat(plan.simType()).isEqualTo(SimType.ESIM);
            assertThat(plan.spec().days()).isEqualTo(5);
            assertThat(plan.spec().dataMb()).isEqualTo(3_072);
            assertThat(plan.price()).isEqualTo(Money.krw(9_900));
        }
    }

    @Nested
    @DisplayName("조건으로 검색하면")
    class Search {

        @Test
        @DisplayName("국가와 SIM 종류가 모두 맞는 요금제만 나온다")
        void filters_by_country_and_sim_type() {
            // when
            List<Plan> plans = planRepository.search(PlanSearchCondition.of("JP", SimType.ESIM, null, null));

            // then
            assertThat(plans).hasSize(2)
                    .allSatisfy(p -> {
                        assertThat(p.spec().countryCode()).isEqualTo("JP");
                        assertThat(p.simType()).isEqualTo(SimType.ESIM);
                    });
        }

        @Test
        @DisplayName("네 조건이 모두 주어지면 정확히 일치하는 하나만 나온다")
        void filters_by_all_conditions() {
            // when
            List<Plan> plans = planRepository.search(PlanSearchCondition.of("JP", SimType.ESIM, 5, 3_072));

            // then
            assertThat(plans).extracting(Plan::id).containsExactly(1L);
        }

        @Test
        @DisplayName("일치하는 요금제가 없으면 빈 목록")
        void empty_when_nothing_matches() {
            // when & then
            assertThat(planRepository.search(PlanSearchCondition.of("KR", null, null, null))).isEmpty();
        }
    }

    @Test
    @DisplayName("없는 id는 빈 Optional")
    void unknown_id() {
        // when & then
        assertThat(planRepository.findById(9_999L)).isEmpty();
    }
}
