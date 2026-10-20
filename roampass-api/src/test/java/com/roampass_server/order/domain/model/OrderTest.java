package com.roampass_server.order.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.IllegalStateTransitionException;
import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;

@DisplayName("Order")
class OrderTest {

    // 2026-10-20 12:00 KST
    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final LocalDate TODAY_KST = LocalDate.of(2026, 10, 20);
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final UserId USER = UserId.of("user-1");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-AAAA1111");
    private static final PlanSnapshot ESIM_PLAN = PlanSnapshot.of(1L, SimType.ESIM, Money.krw(12_000));
    private static final PlanSnapshot USIM_PLAN = PlanSnapshot.of(2L, SimType.USIM, Money.krw(15_000));

    private static Order pending(PlanSnapshot plan) {
        return Order.create(ORDER_NO, USER, plan, TODAY_KST.plusDays(3), NOW, WINDOW);
    }

    private static Order paidEsim() {
        return pending(ESIM_PLAN).confirmPayment(NOW.plusSeconds(60));
    }

    private static Order issuedEsim() {
        return paidEsim().requestIssuance().markIssued(NOW.plusSeconds(120));
    }

    @Nested
    @DisplayName("주문을 만들면")
    class WhenCreated {

        @Test
        @DisplayName("PENDING이고 요금제의 가격·SIM 종류를 주문 시점 값으로 가진다")
        void starts_pending_with_plan_snapshot() {
            // when
            Order order = pending(ESIM_PLAN);

            // then
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.orderNo()).isEqualTo(ORDER_NO);
            assertThat(order.userId()).isEqualTo(USER);
            assertThat(order.planId()).isEqualTo(1L);
            assertThat(order.simType()).isEqualTo(SimType.ESIM);
            assertThat(order.amount()).isEqualTo(Money.krw(12_000));
            assertThat(order.createdAt()).isEqualTo(NOW);
            assertThat(order.id()).isNull();
            assertThat(order.issuedAt()).isNull();
        }

        @Test
        @DisplayName("결제 대기 만료 시각은 생성 시각 + 결제 대기 시간이다")
        void expires_after_payment_window() {
            // when
            Order order = pending(ESIM_PLAN);

            // then
            assertThat(order.expiresAt()).isEqualTo(NOW.plus(WINDOW));
        }

        @Test
        @DisplayName("결제 대기 시간이 0 이하면 거부된다")
        void rejects_non_positive_window() {
            // when & then
            assertThatThrownBy(() -> Order.create(ORDER_NO, USER, ESIM_PLAN, TODAY_KST, NOW, Duration.ZERO))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("필수값이 없으면 거부된다")
        void rejects_missing_fields() {
            // when & then
            assertThatThrownBy(() -> Order.create(null, USER, ESIM_PLAN, TODAY_KST, NOW, WINDOW))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> Order.create(ORDER_NO, null, ESIM_PLAN, TODAY_KST, NOW, WINDOW))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> Order.create(ORDER_NO, USER, null, TODAY_KST, NOW, WINDOW))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("여행 시작일은")
    class TravelStartDate {

        @Test
        @DisplayName("KST 기준 오늘이면 허용된다")
        void accepts_today_in_kst() {
            // when
            Order order = Order.create(ORDER_NO, USER, ESIM_PLAN, TODAY_KST, NOW, WINDOW);

            // then
            assertThat(order.travelStartDate()).isEqualTo(TODAY_KST);
        }

        @Test
        @DisplayName("1년 뒤 같은 날까지 허용된다")
        void accepts_up_to_one_year() {
            // when
            Order order = Order.create(ORDER_NO, USER, ESIM_PLAN, TODAY_KST.plusYears(1), NOW, WINDOW);

            // then
            assertThat(order.travelStartDate()).isEqualTo(TODAY_KST.plusYears(1));
        }

        @Test
        @DisplayName("1년을 넘으면 INVALID_TRAVEL_START_DATE로 거부된다")
        void rejects_more_than_one_year() {
            // when & then
            assertThatThrownBy(() -> Order.create(ORDER_NO, USER, ESIM_PLAN, TODAY_KST.plusYears(1).plusDays(1), NOW, WINDOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_TRAVEL_START_DATE");
        }

        @Test
        @DisplayName("어제면 거부된다")
        void rejects_past_date() {
            // when & then
            assertThatThrownBy(() -> Order.create(ORDER_NO, USER, ESIM_PLAN, TODAY_KST.minusDays(1), NOW, WINDOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_TRAVEL_START_DATE");
        }

        @Test
        @DisplayName("UTC로는 같은 날이어도 KST로 이미 다음 날이면 거부된다 (자정 경계)")
        void uses_kst_today_not_utc_today() {
            // given — 2026-10-20 15:30 UTC is 2026-10-21 00:30 KST
            Instant lateNightUtc = Instant.parse("2026-10-20T15:30:00Z");

            // when & then
            assertThatThrownBy(() -> Order.create(ORDER_NO, USER, ESIM_PLAN, LocalDate.of(2026, 10, 20), lateNightUtc, WINDOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_TRAVEL_START_DATE");
        }
    }

    @Nested
    @DisplayName("결제 대기 만료는")
    class Expiration {

        @Test
        @DisplayName("만료 시각 전에는 조회 상태가 PENDING이고 결제할 수 있다")
        void pending_before_deadline() {
            // given
            Order order = pending(ESIM_PLAN);
            Instant beforeDeadline = order.expiresAt().minusSeconds(1);

            // when & then
            assertThat(order.effectiveStatus(beforeDeadline)).isEqualTo(OrderStatus.PENDING);
            assertThat(order.isPayable(beforeDeadline)).isTrue();
        }

        @Test
        @DisplayName("만료 시각이 되면 스케줄러가 처리하기 전이라도 조회 상태는 EXPIRED이고 결제할 수 없다")
        void effectively_expired_at_deadline() {
            // given
            Order order = pending(ESIM_PLAN);

            // when & then
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.effectiveStatus(order.expiresAt())).isEqualTo(OrderStatus.EXPIRED);
            assertThat(order.isPayable(order.expiresAt())).isFalse();
        }

        @Test
        @DisplayName("만료 시각이 지나면 EXPIRED로 바뀐다")
        void expire_after_deadline() {
            // given
            Order order = pending(ESIM_PLAN);

            // when
            Order expired = order.expire(order.expiresAt());

            // then
            assertThat(expired.status()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(order.status()).isEqualTo(OrderStatus.PENDING); // immutable
        }

        @Test
        @DisplayName("만료 시각 전에 만료시키면 거부된다")
        void cannot_expire_before_deadline() {
            // given
            Order order = pending(ESIM_PLAN);

            // when & then
            assertThatThrownBy(() -> order.expire(order.expiresAt().minusSeconds(1)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("결제된 주문은 만료시킬 수 없다")
        void cannot_expire_paid_order() {
            // when & then
            assertThatThrownBy(() -> paidEsim().expire(NOW.plus(WINDOW).plusSeconds(1)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("결제가 확정되면")
    class PaymentConfirmation {

        @Test
        @DisplayName("이심 주문은 PAID가 된다")
        void esim_becomes_paid() {
            // when
            Order order = pending(ESIM_PLAN).confirmPayment(NOW.plusSeconds(60));

            // then
            assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("유심 주문은 공항 수령 대기(PICKUP_READY)가 된다")
        void usim_becomes_pickup_ready() {
            // when
            Order order = pending(USIM_PLAN).confirmPayment(NOW.plusSeconds(60));

            // then
            assertThat(order.status()).isEqualTo(OrderStatus.PICKUP_READY);
        }

        @Test
        @DisplayName("만료 시각이 지난 뒤 도착한 승인은 주문을 살리지 않고 환불(REFUNDING)로 보낸다")
        void late_approval_after_deadline_goes_to_refunding() {
            // given
            Order order = pending(ESIM_PLAN);

            // when
            Order result = order.confirmPayment(order.expiresAt().plusSeconds(5));

            // then
            assertThat(result.status()).isEqualTo(OrderStatus.REFUNDING);
        }

        @Test
        @DisplayName("이미 EXPIRED로 처리된 주문에 승인이 오면 환불(REFUNDING)로 보낸다")
        void approval_for_expired_order_goes_to_refunding() {
            // given
            Order expired = pending(ESIM_PLAN).expire(NOW.plus(WINDOW));

            // when
            Order result = expired.confirmPayment(NOW.plus(WINDOW).plusSeconds(30));

            // then
            assertThat(result.status()).isEqualTo(OrderStatus.REFUNDING);
        }

        @Test
        @DisplayName("이미 결제 확정된 주문에 다시 확정하면 거부된다 (중복은 결제 단계에서 걸러야 함)")
        void cannot_confirm_twice() {
            // when & then
            assertThatThrownBy(() -> paidEsim().confirmPayment(NOW.plusSeconds(90)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("발급은")
    class Issuance {

        @Test
        @DisplayName("이심: PAID → 발급 요청 → 콜백으로 ISSUED, 발급 시각이 기록된다")
        void esim_issuance_flow() {
            // given
            Instant issuedAt = NOW.plusSeconds(120);

            // when
            Order requested = paidEsim().requestIssuance();
            Order issued = requested.markIssued(issuedAt);

            // then
            assertThat(requested.status()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
            assertThat(issued.status()).isEqualTo(OrderStatus.ISSUED);
            assertThat(issued.issuedAt()).isEqualTo(issuedAt);
        }

        @Test
        @DisplayName("유심: PICKUP_READY에서 ICCID가 등록되면 ISSUED")
        void usim_issued_after_iccid_registration() {
            // given
            Order pickupReady = pending(USIM_PLAN).confirmPayment(NOW.plusSeconds(60));

            // when
            Order issued = pickupReady.markIssued(NOW.plusSeconds(3_600));

            // then
            assertThat(issued.status()).isEqualTo(OrderStatus.ISSUED);
        }

        @Test
        @DisplayName("발급 요청 중 실패하면 ISSUE_FAILED")
        void issuance_can_fail() {
            // when
            Order failed = paidEsim().requestIssuance().markIssueFailed();

            // then
            assertThat(failed.status()).isEqualTo(OrderStatus.ISSUE_FAILED);
        }

        @Test
        @DisplayName("결제 전 주문은 발급을 요청할 수 없다")
        void cannot_request_issuance_before_payment() {
            // when & then
            assertThatThrownBy(() -> pending(ESIM_PLAN).requestIssuance())
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("이미 발급된 주문은 다시 발급될 수 없다")
        void cannot_issue_twice() {
            // when & then
            assertThatThrownBy(() -> issuedEsim().markIssued(NOW.plusSeconds(300)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("환불은")
    class Refund {

        @Test
        @DisplayName("발급 실패 주문은 REFUNDING → REFUNDED로 끝난다")
        void failed_issuance_is_refunded() {
            // given
            Order failed = paidEsim().requestIssuance().markIssueFailed();

            // when
            Order refunding = failed.startRefund(false);
            Order refunded = refunding.completeRefund();

            // then
            assertThat(refunding.status()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(refunded.status()).isEqualTo(OrderStatus.REFUNDED);
        }

        @Test
        @DisplayName("환불이 계속 실패하면 NEEDS_ATTENTION(운영자 확인 대상)으로 간다")
        void repeated_refund_failure_needs_attention() {
            // when
            Order escalated = paidEsim().startRefund(false).escalate();

            // then
            assertThat(escalated.status()).isEqualTo(OrderStatus.NEEDS_ATTENTION);
        }

        @Test
        @DisplayName("발급된 주문은 SIM이 환불 가능할 때만 환불을 시작할 수 있다")
        void issued_order_refund_requires_refundable_sim() {
            // when
            Order refunding = issuedEsim().startRefund(true);

            // then
            assertThat(refunding.status()).isEqualTo(OrderStatus.REFUNDING);
        }

        @Test
        @DisplayName("발급된 주문의 SIM이 설치·개통되어 환불 불가면 ORDER_NOT_REFUNDABLE로 거부된다")
        void issued_order_with_installed_sim_is_not_refundable() {
            // when & then
            assertThatThrownBy(() -> issuedEsim().startRefund(false))
                    .isInstanceOf(OrderNotRefundableException.class)
                    .extracting("code").isEqualTo("ORDER_NOT_REFUNDABLE");
        }

        @Test
        @DisplayName("결제 전 주문은 환불할 수 없다")
        void cannot_refund_unpaid_order() {
            // when & then
            assertThatThrownBy(() -> pending(ESIM_PLAN).startRefund(true))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("운영자 확인 대상에서 수동 환불을 다시 시작할 수 있다")
        void manual_refund_from_needs_attention() {
            // given
            Order needsAttention = paidEsim().startRefund(false).escalate();

            // when
            Order refunding = needsAttention.startRefund(false);

            // then
            assertThat(refunding.status()).isEqualTo(OrderStatus.REFUNDING);
        }
    }

    @Nested
    @DisplayName("운영자 재발급은")
    class Reissue {

        @Test
        @DisplayName("한 번도 발급된 적 없는 이심 주문이면 NEEDS_ATTENTION → ISSUE_REQUESTED")
        void reissue_never_issued_esim() {
            // given
            Order needsAttention = paidEsim().requestIssuance().markIssueFailed().startRefund(false).escalate();

            // when
            Order reissued = needsAttention.reissue();

            // then
            assertThat(reissued.status()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
        }

        @Test
        @DisplayName("이미 발급된 적이 있는 주문은 재발급할 수 없다 (프로필이 두 번 붙지 않게)")
        void cannot_reissue_once_issued() {
            // given
            Order needsAttention = issuedEsim().startRefund(true).escalate();

            // when & then
            assertThatThrownBy(needsAttention::reissue)
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("유심 주문은 재발급 대상이 아니다")
        void cannot_reissue_usim() {
            // given
            Order needsAttention = pending(USIM_PLAN).confirmPayment(NOW.plusSeconds(60)).startRefund(false).escalate();

            // when & then
            assertThatThrownBy(needsAttention::reissue)
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("소유자·동등성")
    class OwnershipAndEquality {

        @Test
        @DisplayName("주문한 사용자만 소유자다")
        void owned_by_creator_only() {
            // given
            Order order = pending(ESIM_PLAN);

            // when & then
            assertThat(order.isOwnedBy(USER)).isTrue();
            assertThat(order.isOwnedBy(UserId.of("user-2"))).isFalse();
        }

        @Test
        @DisplayName("주문번호가 같으면 상태가 달라도 같은 주문이다")
        void equality_is_by_order_no() {
            // given
            Order order = pending(ESIM_PLAN);

            // when & then
            assertThat(order.confirmPayment(NOW.plusSeconds(60))).isEqualTo(order);
        }
    }
}
