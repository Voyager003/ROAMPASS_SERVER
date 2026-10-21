package com.roampass.mock.supplier.fault;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시작 시 장애 주입 설정({@code mock-supplier.faults.*}), 항목 의미는 {@link FaultSettings}
 */
@ConfigurationProperties(prefix = "mock-supplier.faults")
public record FaultProperties(double issueFailureRate, long callbackDelayMs, int callbackDuplicates,
                              boolean callbackDrop, boolean invalidSignature, boolean activationCallbackDrop) {

    public FaultSettings toSettings() {
        return new FaultSettings(issueFailureRate, callbackDelayMs, callbackDuplicates, callbackDrop, invalidSignature,
                activationCallbackDrop);
    }
}
