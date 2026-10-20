package com.roampass.mock.pg.fault;

import org.springframework.http.HttpStatus;

import com.roampass.mock.pg.payment.MockPgException;

/**
 * 장애 주입 설정 한 벌. 불변, 변경은 통째로 교체
 *
 * @param approveFailureRate 승인 실패 확률(0~1), 실패 시 FAILED + 실패 웹훅
 * @param responseDelayMs    승인 응답 지연(0~60000ms), 결제는 먼저 기록되므로 호출 측 타임아웃 시 "처리됐지만 응답 유실" 재현
 * @param webhookDuplicates  같은 웹훅(같은 eventId)을 추가로 보내는 횟수(0~10)
 * @param webhookDrop        웹훅 생략
 * @param webhookAmountDelta 웹훅 금액에 더할 차액(금액 변조), PG 저장 금액은 그대로
 * @param cancelFailureRate  취소 실패 확률(0~1), 실패 시 500 CANCEL_FAILED, 취소는 반영 안 됨
 */
public record FaultSettings(double approveFailureRate, long responseDelayMs, int webhookDuplicates,
                            boolean webhookDrop, long webhookAmountDelta, double cancelFailureRate) {

    private static final long MAX_DELAY_MS = 60_000;
    private static final int MAX_DUPLICATES = 10;

    public FaultSettings {
        requireRate(approveFailureRate, "approveFailureRate");
        requireRate(cancelFailureRate, "cancelFailureRate");
        if (responseDelayMs < 0 || responseDelayMs > MAX_DELAY_MS) {
            throw invalid("responseDelayMs must be between 0 and " + MAX_DELAY_MS);
        }
        if (webhookDuplicates < 0 || webhookDuplicates > MAX_DUPLICATES) {
            throw invalid("webhookDuplicates must be between 0 and " + MAX_DUPLICATES);
        }
    }

    public static FaultSettings none() {
        return new FaultSettings(0, 0, 0, false, 0, 0);
    }

    private static void requireRate(double rate, String name) {
        if (!(rate >= 0 && rate <= 1)) {
            throw invalid(name + " must be between 0 and 1");
        }
    }

    private static MockPgException invalid(String message) {
        return new MockPgException(HttpStatus.BAD_REQUEST, "INVALID_FAULT_CONFIG", message);
    }
}
