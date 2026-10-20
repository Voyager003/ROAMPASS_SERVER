package com.roampass_server.payment.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static com.roampass_server.support.PersistenceTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.model.RefundAttempt;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.model.RetryPolicy;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;
import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@Transactional
@DisplayName("RefundAttemptRepository (PostgreSQL)")
class RefundAttemptRepositoryAdapterIntegrationTest {

    private static final Money PRICE = Money.krw(9_900);

    @Autowired
    private RefundAttemptRepository refundRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    private Payment payment;
    private OrderNo orderNo;

    @BeforeEach
    void savePayment() {
        orderNo = orderRepository.save(pendingOrder("RFND0001", NOW)).orderNo();
        payment = paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW).approve(PgTid.of("pg_r"), PRICE, NOW));
    }

    @Test
    @DisplayName("저장 시 id가 부여되고, 시도 시각이 된 PENDING만 처리 대상으로 조회된다")
    void finds_due() {
        // given
        RefundAttempt saved = refundRepository.save(RefundAttempt.start(payment.idempotencyKey(), orderNo, PRICE, NOW));

        // when // then
        assertThat(saved.id()).isNotNull();
        assertThat(refundRepository.findDue(NOW, 10)).extracting(RefundAttempt::id).contains(saved.id());
        assertThat(refundRepository.findDue(NOW.minusSeconds(1), 10)).extracting(RefundAttempt::id)
                .doesNotContain(saved.id());
        assertThat(refundRepository.findByOrderNo(orderNo)).singleElement()
                .satisfies(r -> assertThat(r.amount()).isEqualTo(PRICE));
    }

    @Test
    @DisplayName("조건부 저장은 기대한 시도 횟수일 때 한 번만 반영된다")
    void saves_once_per_attempt() {
        // given
        RefundAttempt saved = refundRepository.save(RefundAttempt.start(payment.idempotencyKey(), orderNo, PRICE, NOW));
        RefundAttempt failed = saved.recordFailure("timeout", NOW, RetryPolicy.defaults());

        // when
        boolean first = refundRepository.saveIfPending(failed, 0);
        boolean second = refundRepository.saveIfPending(failed, 0);

        // then
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(refundRepository.findByOrderNo(orderNo)).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(RefundStatus.PENDING);
            assertThat(r.attemptCount()).isEqualTo(1);
            assertThat(r.lastError()).isEqualTo("timeout");
            assertThat(r.nextRetryAt()).isEqualTo(NOW.plusSeconds(60));
        });
    }
}
