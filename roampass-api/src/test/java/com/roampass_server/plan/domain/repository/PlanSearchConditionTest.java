package com.roampass_server.plan.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.model.PlanSpec;

@DisplayName("PlanSearchCondition")
class PlanSearchConditionTest {

    private static final Plan JP_ESIM_5D_3GB = Plan.of(1L, PlanSpec.of("JP", 5, 3_072), SimType.ESIM, Money.krw(12_000));

    @Nested
    @DisplayName("조건이 하나도 없으면")
    class WhenEmpty {

        @Test
        @DisplayName("모든 요금제와 일치한다")
        void matches_everything() {
            // when & then
            assertThat(PlanSearchCondition.all().matches(JP_ESIM_5D_3GB)).isTrue();
        }
    }

    @Nested
    @DisplayName("주어진 조건은 정확히 일치해야 한다")
    class ExactMatch {

        @Test
        @DisplayName("국가·SIM 종류·일수·용량이 모두 같으면 일치")
        void matches_all_fields() {
            // when & then
            assertThat(PlanSearchCondition.of("JP", SimType.ESIM, 5, 3_072).matches(JP_ESIM_5D_3GB)).isTrue();
        }

        @Test
        @DisplayName("하나라도 다르면 불일치")
        void any_difference_fails() {
            // when & then
            assertThat(PlanSearchCondition.of("US", null, null, null).matches(JP_ESIM_5D_3GB)).isFalse();
            assertThat(PlanSearchCondition.of(null, SimType.USIM, null, null).matches(JP_ESIM_5D_3GB)).isFalse();
            assertThat(PlanSearchCondition.of(null, null, 7, null).matches(JP_ESIM_5D_3GB)).isFalse();
            assertThat(PlanSearchCondition.of(null, null, null, 5_120).matches(JP_ESIM_5D_3GB)).isFalse();
        }

        @Test
        @DisplayName("일수는 최소값이 아니라 정확히 같아야 한다 (5일 조건에 7일 요금제는 불일치)")
        void days_is_not_a_minimum() {
            // given
            Plan sevenDays = Plan.of(2L, PlanSpec.of("JP", 7, 3_072), SimType.ESIM, Money.krw(15_000));

            // when & then
            assertThat(PlanSearchCondition.of(null, null, 5, null).matches(sevenDays)).isFalse();
        }
    }

    @Nested
    @DisplayName("잘못된 조건은 INVALID_PLAN_FILTER로 거부된다")
    class WhenInvalid {

        @Test
        @DisplayName("국가 코드가 대문자 2글자가 아니면")
        void rejects_malformed_country() {
            // when & then
            assertThatThrownBy(() -> PlanSearchCondition.of("jp", null, null, null))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PLAN_FILTER");
        }

        @Test
        @DisplayName("일수나 용량이 1보다 작으면")
        void rejects_non_positive_numbers() {
            // when & then
            assertThatThrownBy(() -> PlanSearchCondition.of(null, null, 0, null))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> PlanSearchCondition.of(null, null, null, -1))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
