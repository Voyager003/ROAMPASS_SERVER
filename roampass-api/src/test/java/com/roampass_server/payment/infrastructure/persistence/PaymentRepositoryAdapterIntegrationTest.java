package com.roampass_server.payment.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static com.roampass_server.support.PersistenceTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@Transactional
@DisplayName("PaymentRepository (PostgreSQL)")
class PaymentRepositoryAdapterIntegrationTest {

    private static final Money PRICE = Money.krw(9_900);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    private OrderNo orderNo;

    @BeforeEach
    void saveOrder() {
        orderNo = orderRepository.save(pendingOrder("PAYM0001", NOW)).orderNo();
    }

    @Nested
    @DisplayName("저장하고 다시 읽으면")
    class SaveAndFind {

        @Test
        @DisplayName("id가 부여되고 모든 값이 그대로 돌아온다")
        void round_trips_all_fields() {
            // given
            Payment payment = Payment.request(orderNo, 1, PRICE, NOW);

            // when
            Payment saved = paymentRepository.save(payment);
            Payment found = paymentRepository.findByIdempotencyKey(payment.idempotencyKey()).orElseThrow();

            // then
            assertThat(saved.id()).isNotNull();
            assertThat(found.orderNo()).isEqualTo(orderNo);
            assertThat(found.amount()).isEqualTo(PRICE);
            assertThat(found.status()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(found.cancelledAmount()).isEqualTo(Money.krw(0));
            assertThat(found.pgTid()).isNull();
            assertThat(found.requestedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("주문의 시도들을 요청 시각 순으로 돌려준다")
        void finds_attempts_in_order() {
            // given
            paymentRepository.save(Payment.request(orderNo, 2, PRICE, NOW.plusSeconds(60)).fail("x", NOW));
            paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW));

            // when // then
            assertThat(paymentRepository.findByOrderNo(orderNo))
                    .extracting(p -> p.idempotencyKey().value())
                    .containsExactly(orderNo.value() + "-1", orderNo.value() + "-2");
        }

        @Test
        @DisplayName("같은 멱등 키를 다시 저장하면 409 PAYMENT_IN_PROGRESS")
        void rejects_duplicate_key() {
            // given
            paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW));

            // when // then
            assertThatThrownBy(() -> paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW)))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PAYMENT_IN_PROGRESS"));
        }
    }

    @Nested
    @DisplayName("조건부 저장은")
    class SaveIfStatus {

        @Test
        @DisplayName("저장된 상태가 기대와 같으면 승인 값(거래 ID·승인 시각)까지 반영하고, 다시 하면 false")
        void applies_once() {
            // given
            Payment requested = paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW));
            Payment approved = requested.approve(PgTid.of("pg_1"), PRICE, NOW.plusSeconds(3));

            // when
            boolean first = paymentRepository.saveIfStatus(approved, PaymentStatus.REQUESTED);
            boolean second = paymentRepository.saveIfStatus(approved, PaymentStatus.REQUESTED);

            // then
            assertThat(first).isTrue();
            assertThat(second).isFalse();
            Payment found = paymentRepository.findByIdempotencyKey(requested.idempotencyKey()).orElseThrow();
            assertThat(found.status()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(found.pgTid()).isEqualTo(PgTid.of("pg_1"));
            assertThat(found.approvedAt()).isEqualTo(NOW.plusSeconds(3));
        }

        @Test
        @DisplayName("한 주문에 승인된 결제가 이미 있으면 다른 시도의 승인은 DB 제약으로 거부된다")
        void one_approved_payment_per_order() {
            // given
            Payment first = paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW));
            Payment second = paymentRepository.save(Payment.request(orderNo, 2, PRICE, NOW.plusSeconds(1)));
            paymentRepository.saveIfStatus(first.approve(PgTid.of("pg_1"), PRICE, NOW), PaymentStatus.REQUESTED);

            // when // then
            assertThatThrownBy(() -> paymentRepository.saveIfStatus(
                    second.approve(PgTid.of("pg_2"), PRICE, NOW), PaymentStatus.REQUESTED))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("오래된 REQUESTED 조회는")
    class FindStaleRequested {

        @Test
        @DisplayName("기준 시각 이전에 요청된 REQUESTED만, 오래된 순으로, 최대 건수만큼")
        void filters_and_limits() {
            // given
            OrderNo other = orderRepository.save(pendingOrder("PAYM0002", NOW)).orderNo();
            OrderNo third = orderRepository.save(pendingOrder("PAYM0003", NOW)).orderNo();
            paymentRepository.save(Payment.request(orderNo, 1, PRICE, NOW.plusSeconds(10)));
            paymentRepository.save(Payment.request(other, 1, PRICE, NOW));
            paymentRepository.save(Payment.request(third, 1, PRICE, NOW.plusSeconds(5)).fail("x", NOW));
            paymentRepository.save(Payment.request(third, 2, PRICE, NOW.plusSeconds(120)));

            // when
            var stale = paymentRepository.findStaleRequested(NOW.plusSeconds(60), 10);
            var limited = paymentRepository.findStaleRequested(NOW.plusSeconds(60), 1);

            // then
            assertThat(stale).extracting(p -> p.idempotencyKey().value())
                    .containsExactly(other.value() + "-1", orderNo.value() + "-1");
            assertThat(limited).hasSize(1);
        }
    }
}
