package com.roampass_server.plan.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;

@DisplayName("Plan")
class PlanTest {

    private static final PlanSpec SPEC = PlanSpec.of("JP", 5, 3_072);

    @Nested
    @DisplayName("올바른 값으로 만들면")
    class WhenValid {

        @Test
        @DisplayName("식별자·스펙·SIM 종류·가격을 가진다")
        void keeps_values() {
            // when
            Plan plan = Plan.of(1L, SPEC, SimType.ESIM, Money.krw(12_000));

            // then
            assertThat(plan.id()).isEqualTo(1L);
            assertThat(plan.spec()).isEqualTo(SPEC);
            assertThat(plan.simType()).isEqualTo(SimType.ESIM);
            assertThat(plan.price()).isEqualTo(Money.krw(12_000));
        }

        @Test
        @DisplayName("식별자가 같으면 같은 요금제다 (집합 루트는 ID로 구분)")
        void equality_is_by_id() {
            // given
            Plan a = Plan.of(1L, SPEC, SimType.ESIM, Money.krw(12_000));
            Plan b = Plan.of(1L, PlanSpec.of("US", 7, 5_120), SimType.USIM, Money.krw(20_000));
            Plan c = Plan.of(2L, SPEC, SimType.ESIM, Money.krw(12_000));

            // when & then
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
        }
    }

    @Nested
    @DisplayName("잘못된 값으로 만들면 INVALID_PLAN으로 거부된다")
    class WhenInvalid {

        @Test
        @DisplayName("식별자가 없으면")
        void rejects_null_id() {
            // when & then
            assertThatThrownBy(() -> Plan.of(null, SPEC, SimType.ESIM, Money.krw(12_000)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PLAN");
        }

        @Test
        @DisplayName("스펙·SIM 종류·가격 중 하나라도 없으면")
        void rejects_missing_fields() {
            // when & then
            assertThatThrownBy(() -> Plan.of(1L, null, SimType.ESIM, Money.krw(12_000)))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> Plan.of(1L, SPEC, null, Money.krw(12_000)))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> Plan.of(1L, SPEC, SimType.ESIM, null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("가격이 0원이면 (무료 요금제는 판매하지 않음)")
        void rejects_zero_price() {
            // when & then
            assertThatThrownBy(() -> Plan.of(1L, SPEC, SimType.ESIM, Money.krw(0)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_PLAN");
        }
    }
}
