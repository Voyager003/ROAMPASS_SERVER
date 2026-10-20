package com.roampass.mock.supplier.callback;

/**
 * 콜백 발행 포트. 발급 처리 테스트에서 발행 내역만 수집하도록 분리
 */
@FunctionalInterface
public interface CallbackPublisher {

    void publish(CallbackDelivery delivery);
}
