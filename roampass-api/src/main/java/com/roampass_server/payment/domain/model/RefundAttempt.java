package com.roampass_server.payment.domain.model;

import java.time.Instant;

import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;

/**
 * 결제 하나에 대한 환불 요청 1건(집합 루트). PG 취소 재시도는 시도마다 레코드를 만들지 않고
 * 이 레코드 하나에서 추적(시도 횟수, 다음 시도 시각, 마지막 에러)
 * 재시도는 {@link RetryPolicy}로 제한: 허용된 마지막 시도가 실패하면 GAVE_UP, 이후 운영자가 인계
 * 그 뒤의 수동 환불은 새 RefundAttempt라서 자동 처리 기록이 그대로 남음
 * <p>
 * 자연 키 없음(한 주문에 자동 환불과 이후 수동 환불이 따로 있을 수 있음), 따라서 동등성은
 * 객체 동일성이고 영속성 계층에서는 id로 구분
 */
public final class RefundAttempt {

    private static final int MAX_ERROR_LENGTH = 500;

    private final Long id;
    private final IdempotencyKey paymentKey;
    private final OrderNo orderNo;
    private final Money amount;
    private final RefundStatus status;
    private final int attemptCount;
    private final Instant nextRetryAt;
    private final String lastError;
    private final Instant createdAt;
    private final Instant completedAt;

    private RefundAttempt(Long id, IdempotencyKey paymentKey, OrderNo orderNo, Money amount, RefundStatus status,
                          int attemptCount, Instant nextRetryAt, String lastError, Instant createdAt, Instant completedAt) {
        this.id = id;
        this.paymentKey = paymentKey;
        this.orderNo = orderNo;
        this.amount = amount;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    /** 바로 처리 대상인 환불 요청 생성 */
    public static RefundAttempt start(IdempotencyKey paymentKey, OrderNo orderNo, Money amount, Instant now) {
        if (paymentKey == null || orderNo == null || amount == null || now == null) {
            throw new InvalidValueException("INVALID_REFUND", "Payment key, order number, amount and time are required");
        }
        if (amount.amount() == 0) {
            throw new InvalidValueException("INVALID_REFUND", "Refund amount must be greater than zero");
        }
        return new RefundAttempt(null, paymentKey, orderNo, amount, RefundStatus.PENDING, 0, now, null, now, null);
    }

    /** 저장소에서 읽은 값으로 복원, 필수값만 확인 */
    public static RefundAttempt restore(Long id, IdempotencyKey paymentKey, OrderNo orderNo, Money amount,
                                        RefundStatus status, int attemptCount, Instant nextRetryAt, String lastError,
                                        Instant createdAt, Instant completedAt) {
        if (paymentKey == null || orderNo == null || amount == null || status == null || createdAt == null) {
            throw new InvalidValueException("INVALID_REFUND", "Stored refund attempt is missing required values");
        }
        return new RefundAttempt(id, paymentKey, orderNo, amount, status, attemptCount, nextRetryAt, lastError,
                createdAt, completedAt);
    }

    /** 스케줄러가 지금 PG 취소를 시도해야 하는지 여부 */
    public boolean isDue(Instant now) {
        return status == RefundStatus.PENDING && !now.isBefore(nextRetryAt);
    }

    public RefundAttempt recordSuccess(Instant now) {
        requirePending(RefundStatus.SUCCEEDED);
        return new RefundAttempt(id, paymentKey, orderNo, amount, RefundStatus.SUCCEEDED, attemptCount + 1,
                null, lastError, createdAt, now);
    }

    /**
     * 실패(또는 타임아웃)한 PG 취소 기록. 백오프로 다음 시도를 예약하거나,
     * 정책의 시도 횟수 한도에 도달하면 포기
     */
    public RefundAttempt recordFailure(String error, Instant now, RetryPolicy policy) {
        requirePending(RefundStatus.GAVE_UP);
        int failed = attemptCount + 1;
        String trimmed = truncate(error);
        if (policy.isExhausted(failed)) {
            return new RefundAttempt(id, paymentKey, orderNo, amount, RefundStatus.GAVE_UP, failed,
                    null, trimmed, createdAt, now);
        }
        return new RefundAttempt(id, paymentKey, orderNo, amount, RefundStatus.PENDING, failed,
                now.plus(policy.delayAfter(failed)), trimmed, createdAt, null);
    }

    private void requirePending(RefundStatus target) {
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateTransitionException("RefundAttempt", status, target);
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    public Long id() {
        return id;
    }

    public IdempotencyKey paymentKey() {
        return paymentKey;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public Money amount() {
        return amount;
    }

    public RefundStatus status() {
        return status;
    }

    /** 지금까지 시도한 PG 취소 횟수(성공 여부 무관) */
    public int attemptCount() {
        return attemptCount;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public String lastError() {
        return lastError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant completedAt() {
        return completedAt;
    }
}
