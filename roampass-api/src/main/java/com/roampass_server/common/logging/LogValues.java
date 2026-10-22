package com.roampass_server.common.logging;

/**
 * 로그에 남길 값을 다듬는 도구. 개인·민감 값은 로그에 전체를 남기지 않음
 */
public final class LogValues {

    private LogValues() {
    }

    /** ICCID는 뒤 4자리만(운영 화면의 전체 값과 대조하기엔 충분) */
    public static String iccid(String iccid) {
        if (iccid == null || iccid.isBlank()) {
            return "-";
        }
        return iccid.length() <= 4 ? "****" : "****" + iccid.substring(iccid.length() - 4);
    }

    /** 결제 멱등 키({주문번호}-{시도})에서 주문번호. 형식이 다르면 null */
    public static String orderNoOfPaymentKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        int last = idempotencyKey.lastIndexOf('-');
        return last > 0 && idempotencyKey.startsWith("ORD-") ? idempotencyKey.substring(0, last) : null;
    }

    /** 시작 시각(System.nanoTime)부터 지난 밀리초 */
    public static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
