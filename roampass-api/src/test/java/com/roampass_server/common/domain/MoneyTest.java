package com.roampass_server.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("만들 때")
    class WhenCreating {

        @Test
        @DisplayName("금액과 통화를 가진다")
        void has_amount_and_currency() {
            // when
            Money money = Money.krw(10_000);

            // then
            assertThat(money.amount()).isEqualTo(10_000);
            assertThat(money.currency()).isEqualTo(Currency.KRW);
        }

        @Test
        @DisplayName("0원은 허용된다")
        void accepts_zero() {
            // when
            Money money = Money.zero(Currency.KRW);

            // then
            assertThat(money.amount()).isZero();
        }

        @Test
        @DisplayName("음수 금액은 INVALID_MONEY로 거부된다")
        void rejects_negative_amount() {
            // when & then
            assertThatThrownBy(() -> Money.krw(-1))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_MONEY");
        }

        @Test
        @DisplayName("통화가 없으면 거부된다")
        void rejects_null_currency() {
            // when & then
            assertThatThrownBy(() -> Money.of(1_000, null))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_MONEY");
        }

        @Test
        @DisplayName("금액과 통화가 같으면 같은 Money다")
        void equal_when_amount_and_currency_are_equal() {
            // when & then
            assertThat(Money.krw(5_000)).isEqualTo(Money.of(5_000, Currency.KRW));
            assertThat(Money.krw(5_000)).isNotEqualTo(Money.krw(5_001));
        }
    }

    @Nested
    @DisplayName("더하거나 뺄 때")
    class WhenAddingOrSubtracting {

        @Test
        @DisplayName("같은 통화끼리 더하면 합계를 가진 새 Money가 된다")
        void plus_returns_sum() {
            // given
            Money original = Money.krw(10_000);

            // when
            Money result = original.plus(Money.krw(2_500));

            // then
            assertThat(result).isEqualTo(Money.krw(12_500));
            assertThat(original).isEqualTo(Money.krw(10_000)); // immutable
        }

        @Test
        @DisplayName("같은 통화끼리 빼면 차액을 가진 새 Money가 된다")
        void minus_returns_difference() {
            // when
            Money result = Money.krw(10_000).minus(Money.krw(3_000));

            // then
            assertThat(result).isEqualTo(Money.krw(7_000));
        }

        @Test
        @DisplayName("빼서 정확히 0이 되는 것은 허용된다 (전액 취소)")
        void minus_to_zero_is_allowed() {
            // when
            Money result = Money.krw(10_000).minus(Money.krw(10_000));

            // then
            assertThat(result).isEqualTo(Money.zero(Currency.KRW));
        }

        @Test
        @DisplayName("빼서 음수가 되면 INVALID_MONEY로 거부된다 (승인 금액보다 많이 취소할 수 없음)")
        void minus_below_zero_is_rejected() {
            // when & then
            assertThatThrownBy(() -> Money.krw(10_000).minus(Money.krw(10_001)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_MONEY");
        }

        @Test
        @DisplayName("더한 결과가 long 범위를 넘으면 거부된다")
        void plus_overflow_is_rejected() {
            // when & then
            assertThatThrownBy(() -> Money.krw(Long.MAX_VALUE).plus(Money.krw(1)))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_MONEY");
        }
    }

    @Nested
    @DisplayName("비교할 때")
    class WhenComparing {

        @Test
        @DisplayName("금액이 크면 isGreaterThan이 참이다")
        void greater_than() {
            // when & then
            assertThat(Money.krw(10_001).isGreaterThan(Money.krw(10_000))).isTrue();
            assertThat(Money.krw(10_000).isGreaterThan(Money.krw(10_000))).isFalse();
        }

        @Test
        @DisplayName("금액이 같거나 크면 isGreaterThanOrEqualTo가 참이다")
        void greater_than_or_equal_to() {
            // when & then
            assertThat(Money.krw(10_000).isGreaterThanOrEqualTo(Money.krw(10_000))).isTrue();
            assertThat(Money.krw(9_999).isGreaterThanOrEqualTo(Money.krw(10_000))).isFalse();
        }
    }

    @Nested
    @DisplayName("통화가 다른 Money와 연산하면")
    class WhenCurrenciesDiffer {

        private final Money krw = Money.krw(1_000);
        private final Money usd = Money.of(1_000, Currency.USD);

        @Test
        @DisplayName("더하기는 CURRENCY_MISMATCH로 거부된다")
        void plus_rejects_currency_mismatch() {
            // when & then
            assertThatThrownBy(() -> krw.plus(usd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("CURRENCY_MISMATCH");
        }

        @Test
        @DisplayName("빼기는 CURRENCY_MISMATCH로 거부된다")
        void minus_rejects_currency_mismatch() {
            // when & then
            assertThatThrownBy(() -> krw.minus(usd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("CURRENCY_MISMATCH");
        }

        @Test
        @DisplayName("비교는 CURRENCY_MISMATCH로 거부된다")
        void comparison_rejects_currency_mismatch() {
            // when & then
            assertThatThrownBy(() -> krw.isGreaterThan(usd))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("CURRENCY_MISMATCH");
        }

        @Test
        @DisplayName("금액이 같아도 통화가 다르면 같은 Money가 아니다")
        void not_equal_across_currencies() {
            // when & then
            assertThat(krw).isNotEqualTo(usd);
        }
    }
}
