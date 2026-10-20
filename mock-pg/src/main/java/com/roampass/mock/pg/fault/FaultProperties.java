package com.roampass.mock.pg.fault;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시작 시 장애 주입 설정({@code mock-pg.faults.*}), 항목 의미는 {@link FaultSettings}
 */
@ConfigurationProperties(prefix = "mock-pg.faults")
public record FaultProperties(double approveFailureRate, long responseDelayMs, int webhookDuplicates,
                              boolean webhookDrop, long webhookAmountDelta, double cancelFailureRate) {

    public FaultSettings toSettings() {
        return new FaultSettings(approveFailureRate, responseDelayMs, webhookDuplicates, webhookDrop,
                webhookAmountDelta, cancelFailureRate);
    }
}
