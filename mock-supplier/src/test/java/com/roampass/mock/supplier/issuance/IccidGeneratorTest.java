package com.roampass.mock.supplier.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

@DisplayName("ICCID 생성")
class IccidGeneratorTest {

    private final IccidGenerator generator = new IccidGenerator();

    @RepeatedTest(20)
    @DisplayName("19자리 숫자이고 Luhn 체크 디지트가 맞는다")
    void valid_luhn() {
        // when
        String iccid = generator.next();

        // then
        assertThat(iccid).hasSize(19).containsOnlyDigits().startsWith("89");
        assertThat(luhnSum(iccid) % 10).isZero();
    }

    @Test
    @DisplayName("연속 생성 값이 서로 다르다")
    void unique() {
        // when
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(generator.next());
        }

        // then
        assertThat(seen).hasSize(100);
    }

    private static int luhnSum(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum;
    }
}
