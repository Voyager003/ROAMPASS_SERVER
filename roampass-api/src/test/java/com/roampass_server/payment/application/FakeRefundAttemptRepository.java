package com.roampass_server.payment.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

/**
 * 테스트용 인메모리 RefundAttemptRepository. 저장 시 id 부여
 */
public class FakeRefundAttemptRepository implements RefundAttemptRepository {

    private final List<RefundAttempt> attempts = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized RefundAttempt save(RefundAttempt attempt) {
        RefundAttempt saved = RefundAttempt.restore(sequence.incrementAndGet(), attempt.paymentKey(),
                attempt.orderNo(), attempt.amount(), attempt.status(), attempt.attemptCount(), attempt.nextRetryAt(),
                attempt.lastError(), attempt.createdAt(), attempt.completedAt());
        attempts.add(saved);
        return saved;
    }

    @Override
    public synchronized List<RefundAttempt> findDue(Instant now, int limit) {
        return attempts.stream().filter(a -> a.isDue(now))
                .sorted(Comparator.comparing(RefundAttempt::nextRetryAt)).limit(limit).toList();
    }

    @Override
    public synchronized List<RefundAttempt> findByOrderNo(OrderNo orderNo) {
        return attempts.stream().filter(a -> a.orderNo().equals(orderNo)).toList();
    }

    @Override
    public synchronized boolean saveIfPending(RefundAttempt attempt, int expectedAttemptCount) {
        for (int i = 0; i < attempts.size(); i++) {
            RefundAttempt current = attempts.get(i);
            if (current.id().equals(attempt.id())) {
                if (current.status() != RefundStatus.PENDING || current.attemptCount() != expectedAttemptCount) {
                    return false;
                }
                attempts.set(i, attempt);
                return true;
            }
        }
        return false;
    }
}
