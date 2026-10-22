package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Instant;

import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;
import com.roampass_server.payment.domain.service.PgCancelRequest;
import com.roampass_server.payment.domain.service.PgCancelResult;
import com.roampass_server.payment.domain.service.PgClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 시도 시각이 된 환불 요청을 PG 취소로 처리. 스케줄러가 주기적으로 호출, 트랜잭션 없음(PG 호출은 밖)
 * 취소 키 {@code refund-{환불 요청 id}}는 재시도해도 같아서 PG가 한 번만 취소(타임아웃 후 재전송 안전)
 */
@Slf4j
public class RefundExecutionService {

    private final RefundAttemptRepository refundAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final RefundResultService resultService;
    private final Clock clock;
    private final int batchSize;

    public RefundExecutionService(RefundAttemptRepository refundAttemptRepository, PaymentRepository paymentRepository,
                                  PgClient pgClient, RefundResultService resultService, Clock clock, int batchSize) {
        this.refundAttemptRepository = refundAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.pgClient = pgClient;
        this.resultService = resultService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /** @return 환불 완료한 건수 */
    public int processDue() {
        int refunded = 0;
        for (RefundAttempt attempt : refundAttemptRepository.findDue(Instant.now(clock), batchSize)) {
            if (execute(attempt)) {
                refunded++;
            }
        }
        return refunded;
    }

    private boolean execute(RefundAttempt attempt) {
        Payment payment = paymentRepository.findByIdempotencyKey(attempt.paymentKey()).orElseThrow();
        PgCancelResult result = pgClient.cancel(
                new PgCancelRequest(payment.pgTid(), "refund-" + attempt.id(), attempt.amount()));
        if (result.outcome() == PgCancelResult.Outcome.CANCELLED) {
            return resultService.applySuccess(attempt);
        }
        log.warn("event=refund_attempt_failed refundId={} orderNo={} outcome={} reason=\"{}\"", attempt.id(),
                attempt.orderNo().value(), result.outcome(), result.reason());
        resultService.applyFailure(attempt, result.outcome() + ": " + result.reason());
        return false;
    }
}
