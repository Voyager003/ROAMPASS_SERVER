package com.roampass.mock.supplier;

/** 로그용 값 다듬기: ICCID는 뒤 4자리만(roampass-api와 같은 규칙이라 두 로그를 대조할 수 있음) */
public final class LogMask {

    private LogMask() {
    }

    public static String iccid(String iccid) {
        if (iccid == null || iccid.isBlank()) {
            return "-";
        }
        return iccid.length() <= 4 ? "****" : "****" + iccid.substring(iccid.length() - 4);
    }
}
