package com.roampass.mock.supplier.callback;

public enum CallbackEventType {
    /** 이심 발급 완료(ICCID·활성화 코드) */
    ISSUED,
    /** 이심 발급 실패 */
    ISSUE_FAILED,
    /** 개통 이벤트(과제 2.3-④ "개통 이벤트 콜백"). 프로필이 처음 ENABLED가 될 때 1번, 가끔 빠질 수 있음 */
    ACTIVATED
}
