package com.roampass_server.payment.application;

import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.model.HistoryTarget;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

/**
 * 주문의 승인된 결제에 대해 남은 금액 전부를 돌려주는 환불 요청 생성
 * 주문을 REFUNDING으로 바꾸는 쪽(발급 실패·만료 후 승인)의 트랜잭션 안에서 호출, PG 취소는 환불 실행 스케줄러가
 */
public class RefundOpener {

    private final PaymentRepository paymentRepository;
    private final RefundAttemptRepository refundAttemptRepository;
    private final StatusHistoryRecorder historyRecorder;

    public RefundOpener(PaymentRepository paymentRepository, RefundAttemptRepository refundAttemptRepository,
                        StatusHistoryRecorder historyRecorder) {
        this.paymentRepository = paymentRepository;
        this.refundAttemptRepository = refundAttemptRepository;
        this.historyRecorder = historyRecorder;
    }

    @Transactional
    public RefundAttempt open(OrderNo orderNo, Actor actor, String reason, Instant now) {
        Payment payment = paymentRepository.findByOrderNo(orderNo).stream()
                .filter(p -> p.cancelableAmount().amount() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No refundable payment for order " + orderNo.value()));
        RefundAttempt saved = refundAttemptRepository.save(
                RefundAttempt.start(payment.idempotencyKey(), orderNo, payment.cancelableAmount(), now));
        historyRecorder.record(orderNo, HistoryTarget.of(TargetType.REFUND, String.valueOf(saved.id())), null,
                RefundStatus.PENDING, actor, reason, now);
        return saved;
    }
}
