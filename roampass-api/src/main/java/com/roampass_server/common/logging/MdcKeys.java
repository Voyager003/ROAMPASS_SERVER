package com.roampass_server.common.logging;

/**
 * 로그 추적 열쇠(MDC 키). 모든 로그 줄에 붙고, prod의 ECS JSON에서는 같은 이름의 필드가 됨
 */
public final class MdcKeys {

    /** 요청 하나를 묶는 ID(X-Request-Id) */
    public static final String REQUEST_ID = "requestId";
    /** 주문번호: 경로·웹훅·콜백에서 알 수 있을 때 */
    public static final String ORDER_NO = "orderNo";

    private MdcKeys() {
    }
}
