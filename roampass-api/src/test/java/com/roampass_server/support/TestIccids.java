package com.roampass_server.support;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 테스트용 ICCID 생성: 89 + 랜덤 16자리 + Luhn 체크 디지트(19자리)
 * 실제 DB를 쓰는 테스트에서 매번 달라 ICCID 유니크 제약과 충돌하지 않음
 */
public final class TestIccids {

    private TestIccids() {
    }

    public static String random() {
        StringBuilder digits = new StringBuilder("89");
        for (int i = 0; i < 16; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        int sum = 0;
        boolean doubleIt = true;
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
        return digits.append((10 - sum % 10) % 10).toString();
    }
}
