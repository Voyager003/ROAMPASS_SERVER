package com.roampass_server.issuance.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * SIM 카드 일련번호. 19~20자리 숫자, 마지막 자리는 Luhn 체크 디지트
 * 사용자가 유심 카드에 인쇄된 번호를 잘못 입력해도 공급사 호출 전에 차단
 */
public record Iccid(String value) {

    private static final String CODE = "INVALID_ICCID";
    private static final Pattern DIGITS = Pattern.compile("\\d{19,20}");

    public Iccid {
        if (value == null || !DIGITS.matcher(value).matches()) {
            throw new InvalidValueException(CODE, "ICCID must be 19 or 20 digits");
        }
        if (!passesLuhn(value)) {
            throw new InvalidValueException(CODE, "ICCID check digit is invalid");
        }
    }

    public static Iccid of(String value) {
        return new Iccid(value);
    }

    // Luhn: 가장 오른쪽 자리부터 한 자리 건너 하나씩 두 배(9보다 크면 9를 뺌), 합계의 끝자리가 0이면 유효
    private static boolean passesLuhn(String digits) {
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
        return sum % 10 == 0;
    }
}
