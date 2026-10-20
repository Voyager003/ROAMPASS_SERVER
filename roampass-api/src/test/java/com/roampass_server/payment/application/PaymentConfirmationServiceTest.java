package com.roampass_server.payment.application;

import static com.roampass_server.payment.application.PaymentFixture.ORDER_NO;
import static com.roampass_server.payment.application.PaymentFixture.PRICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.order.application.FakeOrderRepository;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.IdempotencyKey;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;

@DisplayName("결제 확정 유스케이스")
class PaymentConfirmationServiceTest {

    private static final PgTid TID = PgTid.of("pg_1");

    private final PaymentFixture f = new PaymentFixture();

    private IdempotencyKey key(Payment payment) {
        return payment.idempotencyKey();
    }

    private OrderStatus orderStatus() {
        return f.orders.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    private PaymentStatus paymentStatus(Payment payment) {
        return f.payments.findByIdempotencyKey(payment.idempotencyKey()).orElseThrow().status();
    }

    @Nested
    @DisplayName("승인 통지가 오면")
    class Approval {

        @Test
        @DisplayName("결제 대기 중 이심 주문은 결제 APPROVED, 주문 PAID, 이력 2건(결제·주문)")
        void confirms_esim_order() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);

            // when
            ConfirmationResult result = f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.CONFIRMED);
            assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.APPROVED);
            assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .extracting(h -> h.target().type(), StatusHistory::fromStatus, StatusHistory::toStatus,
                            StatusHistory::actor)
                    .containsExactly(
                            tuple(TargetType.PAYMENT, "REQUESTED", "APPROVED", Actor.pg()),
                            tuple(TargetType.ORDER, "PENDING", "PAID", Actor.pg()));
        }

        @Test
        @DisplayName("유심 주문은 PICKUP_READY")
        void confirms_usim_order() {
            // given
            f.pendingOrder(SimType.USIM);
            Payment payment = f.requestedPayment(1);

            // when
            f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // then
            assertThat(orderStatus()).isEqualTo(OrderStatus.PICKUP_READY);
        }

        @Test
        @DisplayName("같은 승인이 다시 오면 ALREADY_APPLIED, 상태·이력 변화 없음")
        void duplicate_is_noop() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);
            f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // when
            ConfirmationResult result = f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.ALREADY_APPLIED);
            assertThat(f.histories.all()).hasSize(2);
        }

        @Test
        @DisplayName("금액이 다르면 AMOUNT_MISMATCH, 결제 REQUESTED·주문 PENDING 그대로, 이력 없음")
        void amount_mismatch() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);

            // when
            ConfirmationResult result = f.confirmation.confirmApproval(key(payment), TID, Money.krw(10_000),
                    Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.AMOUNT_MISMATCH);
            assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(orderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(f.histories.all()).isEmpty();
        }

        @Test
        @DisplayName("만료 시각 이후 도착하면(저장 상태 PENDING) 주문 REFUNDING, 이력에 만료(SYSTEM)·환불 시작(PG)")
        void late_approval_on_pending_order() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);
            f.clock.advance(Duration.ofMinutes(10));

            // when
            ConfirmationResult result = f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.LATE_APPROVAL_REFUNDING);
            assertThat(orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> h.target().type() == TargetType.ORDER)
                    .extracting(StatusHistory::fromStatus, StatusHistory::toStatus, StatusHistory::actor)
                    .containsExactly(
                            tuple("PENDING", "EXPIRED", Actor.system()),
                            tuple("EXPIRED", "REFUNDING", Actor.pg()));
        }

        @Test
        @DisplayName("만료 스케줄러가 이미 EXPIRED로 바꾼 주문이면 REFUNDING, 주문 이력 1건")
        void late_approval_on_expired_order() {
            // given
            Order order = f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);
            f.clock.advance(Duration.ofMinutes(10));
            f.orders.replace(order.expire(f.clock.instant()));

            // when
            f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // then
            assertThat(orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> h.target().type() == TargetType.ORDER)
                    .extracting(StatusHistory::fromStatus, StatusHistory::toStatus)
                    .containsExactly(tuple("EXPIRED", "REFUNDING"));
        }

        @Test
        @DisplayName("주문을 읽은 뒤 저장 전에 만료 스케줄러가 끼어들면 다시 읽어 REFUNDING으로 반영")
        void retries_when_expired_concurrently() {
            // given
            Order order = f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);
            f.clock.advance(Duration.ofMinutes(10));
            var racingOrders = new FakeOrderRepository() {
                private boolean raced;

                @Override
                public boolean saveIfStatus(Order changed, OrderStatus expected) {
                    if (!raced) {
                        raced = true;
                        replace(order.expire(f.clock.instant())); // 스케줄러가 먼저 커밋
                    }
                    return super.saveIfStatus(changed, expected);
                }
            };
            racingOrders.save(order);
            var confirmation = new PaymentConfirmationService(f.payments, racingOrders, f.recorder, f.clock);

            // when
            ConfirmationResult result = confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.LATE_APPROVAL_REFUNDING);
            assertThat(racingOrders.findByOrderNo(ORDER_NO)).get()
                    .extracting(Order::status).isEqualTo(OrderStatus.REFUNDING);
        }

        @Test
        @DisplayName("없는 결제면 PAYMENT_NOT_FOUND")
        void unknown_payment() {
            // when
            ConfirmationResult result = f.confirmation.confirmApproval(
                    IdempotencyKey.of(ORDER_NO, 9), TID, PRICE, Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.PAYMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("실패 통지가 오면")
    class Failure {

        @Test
        @DisplayName("결제 FAILED, 주문은 PENDING 유지(재결제 가능), 결제 이력 1건")
        void marks_failed() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);

            // when
            ConfirmationResult result = f.confirmation.confirmFailure(key(payment), "CARD_DECLINED", Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.FAILED);
            assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.FAILED);
            assertThat(orderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(f.histories.all()).singleElement()
                    .extracting(StatusHistory::toStatus).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("같은 실패가 다시 오면 ALREADY_APPLIED")
        void duplicate_failure() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);
            f.confirmation.confirmFailure(key(payment), "CARD_DECLINED", Actor.pg());

            // when // then
            assertThat(f.confirmation.confirmFailure(key(payment), "CARD_DECLINED", Actor.pg()))
                    .isEqualTo(ConfirmationResult.ALREADY_APPLIED);
            assertThat(f.histories.all()).hasSize(1);
        }

        @Test
        @DisplayName("이미 승인된 결제면 IGNORED, 승인은 뒤집지 않음")
        void failure_after_approval() {
            // given
            f.pendingOrder(SimType.ESIM);
            Payment payment = f.requestedPayment(1);
            f.confirmation.confirmApproval(key(payment), TID, PRICE, Actor.pg());

            // when
            ConfirmationResult result = f.confirmation.confirmFailure(key(payment), "late failure", Actor.pg());

            // then
            assertThat(result).isEqualTo(ConfirmationResult.IGNORED);
            assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.APPROVED);
            assertThat(orderStatus()).isEqualTo(OrderStatus.PAID);
        }
    }
}
