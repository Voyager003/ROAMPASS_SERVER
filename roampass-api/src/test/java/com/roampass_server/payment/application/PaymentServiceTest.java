package com.roampass_server.payment.application;

import static com.roampass_server.payment.application.PaymentFixture.ORDER_NO;
import static com.roampass_server.payment.application.PaymentFixture.PRICE;
import static com.roampass_server.payment.application.PaymentFixture.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.service.PgApproveResult;

@DisplayName("결제 요청 유스케이스")
class PaymentServiceTest {

    private final PaymentFixture f = new PaymentFixture();
    private final FakePgClient pg = new FakePgClient();
    private final PaymentService service = new PaymentService(
            new PaymentRequestService(f.orders, f.payments, f.recorder, f.clock), f.confirmation, pg,
            f.payments, f.orders, f.clock);

    @Nested
    @DisplayName("PG가 승인하면")
    class Approved {

        @Test
        @DisplayName("첫 시도 멱등 키로 PG를 한 번 호출하고 결제 APPROVED, 주문 PAID")
        void pays() {
            // given
            f.pendingOrder(SimType.ESIM);

            // when
            PayResult result = service.pay(USER, ORDER_NO);

            // then
            assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(pg.requests()).singleElement().satisfies(request -> {
                assertThat(request.idempotencyKey().value()).isEqualTo("ORD-20261020-AAAA1111-1");
                assertThat(request.amount()).isEqualTo(PRICE);
            });
        }

        @Test
        @DisplayName("결제 요청(USER)·승인(PG)·주문 확정(PG) 이력이 남는다")
        void records_history() {
            // given
            f.pendingOrder(SimType.ESIM);

            // when
            service.pay(USER, ORDER_NO);

            // then
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .extracting(h -> h.target().type(), StatusHistory::toStatus, StatusHistory::actor)
                    .containsExactly(
                            tuple(TargetType.PAYMENT, "REQUESTED", Actor.user(USER)),
                            tuple(TargetType.PAYMENT, "APPROVED", Actor.pg()),
                            tuple(TargetType.ORDER, "PAID", Actor.pg()));
        }
    }

    @Nested
    @DisplayName("PG가 거절하면")
    class Failed {

        @Test
        @DisplayName("결제 FAILED, 주문 PENDING이고 다시 결제하면 2번째 시도 키로 요청")
        void retry_after_failure() {
            // given
            f.pendingOrder(SimType.ESIM);
            pg.willReturn(PgApproveResult.failed("CARD_DECLINED"));
            PayResult first = service.pay(USER, ORDER_NO);
            pg.willReturn(null);

            // when
            PayResult second = service.pay(USER, ORDER_NO);

            // then
            assertThat(first.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(first.orderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(second.idempotencyKey().value()).isEqualTo("ORD-20261020-AAAA1111-2");
            assertThat(second.orderStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

    @Nested
    @DisplayName("PG 결과를 모르면(타임아웃 등)")
    class Unknown {

        @Test
        @DisplayName("결제는 REQUESTED로 남고, 결과가 확정될 때까지 새 결제는 409 PAYMENT_IN_PROGRESS")
        void blocks_new_attempt_until_settled() {
            // given
            f.pendingOrder(SimType.ESIM);
            pg.willReturn(PgApproveResult.unknown("read timeout"));

            // when
            PayResult result = service.pay(USER, ORDER_NO);

            // then
            assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.PENDING);
            assertThatThrownBy(() -> service.pay(USER, ORDER_NO))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PAYMENT_IN_PROGRESS"));
            assertThat(pg.requests()).hasSize(1);
        }

        @Test
        @DisplayName("승인 금액이 다르면 결제는 REQUESTED로 남는다(확정하지 않음)")
        void mismatched_response_is_not_confirmed() {
            // given
            f.pendingOrder(SimType.ESIM);
            pg.willReturn(PgApproveResult.approved(PgTid.of("pg_1"), Money.krw(1)));

            // when
            PayResult result = service.pay(USER, ORDER_NO);

            // then
            assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("결제할 수 없는 주문이면")
    class NotPayable {

        @Test
        @DisplayName("만료된 주문은 409 ORDER_NOT_PAYABLE, 결제를 만들지 않고 PG도 호출하지 않음")
        void expired_order() {
            // given
            f.pendingOrder(SimType.ESIM);
            f.clock.advance(Duration.ofMinutes(10));

            // when // then
            assertThatThrownBy(() -> service.pay(USER, ORDER_NO))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_PAYABLE"));
            assertThat(f.payments.count()).isZero();
            assertThat(pg.requests()).isEmpty();
        }

        @Test
        @DisplayName("이미 결제된 주문은 409 ORDER_NOT_PAYABLE")
        void already_paid() {
            // given
            f.pendingOrder(SimType.ESIM);
            service.pay(USER, ORDER_NO);

            // when // then
            assertThatThrownBy(() -> service.pay(USER, ORDER_NO))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_PAYABLE"));
        }

        @Test
        @DisplayName("남의 주문은 404 ORDER_NOT_FOUND")
        void other_users_order() {
            // given
            f.pendingOrder(SimType.ESIM);

            // when // then
            assertThatThrownBy(() -> service.pay(UserId.of("user-2"), ORDER_NO))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }
    }
}
