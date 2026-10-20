package com.roampass.mock.supplier.issuance;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 19자리 ICCID 생성: 89(통신 산업 식별자) + 랜덤 16자리 + Luhn 체크 디지트
 */
public class IccidGenerator {

    public String next() {
        StringBuilder digits = new StringBuilder("89");
        for (int i = 0; i < 16; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        return digits.append(checkDigit(digits.toString())).toString();
    }

    // 체크 디지트 자리를 포함해 오른쪽부터 한 자리 건너 두 배, 합계가 10의 배수가 되는 마지막 자리
    private static int checkDigit(String payload) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }
}
