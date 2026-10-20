package com.roampass_server.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.service.PgClient;
import com.roampass_server.payment.domain.service.PgInquiryResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 응답을 못 받은 결제(타임아웃·응답 유실·웹훅 누락) 정리. 스케줄러가 주기적으로 호출
 * 요청 후 기준 시간이 지난 REQUESTED를 PG에 조회해 결제 확정 유스케이스로 반영
 * 트랜잭션 없음: PG 조회는 트랜잭션 밖, 반영은 확정 유스케이스의 트랜잭션
 * 기준 시간은 PG 응답 타임아웃보다 길어야 함(아직 응답을 기다리는 결제를 건드리지 않도록)
 */
@Slf4j
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PaymentConfirmationService confirmationService;
    private final Clock clock;
    private final Duration staleAfter;
    private final int batchSize;

    public PaymentReconciliationService(PaymentRepository paymentRepository, PgClient pgClient,
                                        PaymentConfirmationService confirmationService, Clock clock,
                                        Duration staleAfter, int batchSize) {
        this.paymentRepository = paymentRepository;
        this.pgClient = pgClient;
        this.confirmationService = confirmationService;
        this.clock = clock;
        this.staleAfter = staleAfter;
        this.batchSize = batchSize;
    }

    /**
     * @return 승인·실패로 확정한 건수(조회 실패·금액 불일치로 남긴 건 제외)
     */
    public int reconcile() {
        Instant threshold = Instant.now(clock).minus(staleAfter);
        int settled = 0;
        for (Payment payment : paymentRepository.findStaleRequested(threshold, batchSize)) {
            if (settle(payment)) {
                settled++;
            }
        }
        return settled;
    }

    private boolean settle(Payment payment) {
        PgInquiryResult result = pgClient.inquire(payment.idempotencyKey());
        ConfirmationResult confirmation = switch (result.status()) {
            case APPROVED -> confirmationService.confirmApproval(payment.idempotencyKey(), result.pgTid(),
                    result.amount(), Actor.system());
            case FAILED -> confirmationService.confirmFailure(payment.idempotencyKey(), "PG 조회 결과 실패",
                    Actor.system());
            // 승인 요청이 PG에 닿지 않음: 돈이 나가지 않았으므로 실패로 닫아 재결제 허용
            case NOT_FOUND -> confirmationService.confirmFailure(payment.idempotencyKey(), "PG 미수신",
                    Actor.system());
            case UNKNOWN -> null;
        };
        if (confirmation == ConfirmationResult.AMOUNT_MISMATCH) {
            log.error("Payment {} approved at PG with a different amount, needs operator review",
                    payment.idempotencyKey().value());
        }
        return confirmation == ConfirmationResult.CONFIRMED
                || confirmation == ConfirmationResult.LATE_APPROVAL_REFUNDING
                || confirmation == ConfirmationResult.FAILED;
    }
}
