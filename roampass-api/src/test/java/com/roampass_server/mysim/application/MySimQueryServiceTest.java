package com.roampass_server.mysim.application;

import static com.roampass_server.issuance.application.IssuanceFixture.NOW;
import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.IssuanceFixture;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.mysim.application.IssuanceWaiting.Action;
import com.roampass_server.mysim.application.IssuanceWaiting.Stage;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.model.PlanSnapshot;

@DisplayName("내 SIM 조회")
class MySimQueryServiceTest {

    private static final UserId USER = UserId.of("user-1");

    private final IssuanceFixture f = new IssuanceFixture();
    private final MySimQueryService service = new MySimQueryService(f.orders, f.profiles, f.requests, f.clock,
            IssuanceFixture.CALLBACK_TIMEOUT);

    private Order pendingOrder(String orderNo, UserId owner) {
        return f.orders.save(Order.create(OrderNo.of(orderNo), owner, PlanSnapshot.of(1L, SimType.ESIM,
                IssuanceFixture.PRICE), LocalDate.of(2026, 10, 25), NOW, Duration.ofMinutes(10)));
    }

    private void activate() {
        f.applier.apply(f.profiles.findByOrderNo(ORDER_NO).orElseThrow(),
                SupplierProfileResult.found(SupplierProfileState.ENABLED, NOW, 0));
    }

    @Nested
    @DisplayName("목록은")
    class ListSims {

        @Test
        @DisplayName("본인의 결제된 주문만 보여 준다 (결제 대기 주문·남의 주문 제외)")
        void only_my_paid_orders() {
            // given
            f.issued();
            f.pickupReadyUsimOrder(OrderNo.of("ORD-20261021-USIM0001"));
            pendingOrder("ORD-20261021-PEND0001", USER);
            f.orders.replace(pendingOrder("ORD-20261021-OTHR0001", UserId.of("user-2")).confirmPayment(NOW));

            // when
            PageResult<MySimSummary> page = service.list(USER, 0, 20);

            // then
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.content()).extracting(MySimSummary::orderNo)
                    .containsExactlyInAnyOrder(ORDER_NO.value(), "ORD-20261021-USIM0001");
        }

        @Test
        @DisplayName("행마다 설치 상태를 함께 보여 준다 (발급 전이면 없음)")
        void with_install_status() {
            // given
            f.issued();
            f.pickupReadyUsimOrder(OrderNo.of("ORD-20261021-USIM0001"));

            // when
            PageResult<MySimSummary> page = service.list(USER, 0, 20);

            // then
            assertThat(page.content()).filteredOn(s -> s.orderNo().equals(ORDER_NO.value())).singleElement()
                    .satisfies(s -> {
                        assertThat(s.orderStatus()).isEqualTo(OrderStatus.ISSUED);
                        assertThat(s.installStatus()).isEqualTo(InstallStatus.ISSUED);
                        assertThat(s.amount()).isEqualTo(IssuanceFixture.PRICE);
                    });
            assertThat(page.content()).filteredOn(s -> s.orderNo().equals("ORD-20261021-USIM0001")).singleElement()
                    .satisfies(s -> assertThat(s.installStatus()).isNull());
        }
    }

    @Nested
    @DisplayName("상세는")
    class Detail {

        @Test
        @DisplayName("발급된 이심이면 결제 금액·ICCID·활성화 코드를 주고, 개통 전이라 기간은 없고 환불 가능")
        void issued_esim() {
            // given
            f.issued();

            // when
            MySimDetail detail = service.detail(USER, ORDER_NO);

            // then
            assertThat(detail.amount()).isEqualTo(IssuanceFixture.PRICE);
            assertThat(detail.orderStatus()).isEqualTo(OrderStatus.ISSUED);
            assertThat(detail.refundable()).isTrue();
            assertThat(detail.sim().iccid()).isEqualTo(IssuanceFixture.ICCID);
            assertThat(detail.sim().activationCode()).isEqualTo(IssuanceFixture.ACTIVATION_CODE);
            assertThat(detail.sim().installStatus()).isEqualTo(InstallStatus.ISSUED);
            assertThat(detail.sim().expiresAt()).isNull();
            assertThat(detail.sim().remainingSeconds()).isNull();
        }

        @Test
        @DisplayName("개통된 SIM이면 만료 시각과 남은 시간을 주고, 환불 불가")
        void activated() {
            // given
            f.issued();
            activate();
            f.clock.advance(Duration.ofDays(1));

            // when
            MySimDetail detail = service.detail(USER, ORDER_NO);

            // then
            assertThat(detail.refundable()).isFalse();
            assertThat(detail.sim().installStatus()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(detail.sim().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(5)));
            assertThat(detail.sim().expiresAtKst()).isEqualTo(LocalDateTime.of(2026, 10, 26, 9, 0));
            assertThat(detail.sim().remainingSeconds()).isEqualTo(Duration.ofDays(4).toSeconds());
        }

        @Test
        @DisplayName("사용 기간이 지나면 남은 시간은 0")
        void expired() {
            // given
            f.issued();
            activate();
            f.clock.advance(Duration.ofDays(6));

            // when // then
            assertThat(service.detail(USER, ORDER_NO).sim().remainingSeconds()).isZero();
        }

        @Test
        @DisplayName("발급 전(PAID)이면 SIM 정보는 없고 환불 가능")
        void paid_before_issue() {
            // given
            f.paidEsimOrder();

            // when
            MySimDetail detail = service.detail(USER, ORDER_NO);

            // then
            assertThat(detail.orderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(detail.sim()).isNull();
            assertThat(detail.refundable()).isTrue();
        }

        @Test
        @DisplayName("공급사에 발급 요청 중(ISSUE_REQUESTED)이면 환불 불가")
        void issue_requested() {
            // given
            f.dispatched();

            // when
            MySimDetail detail = service.detail(USER, ORDER_NO);

            // then
            assertThat(detail.orderStatus()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
            assertThat(detail.refundable()).isFalse();
        }

        @Test
        @DisplayName("등록된 유심이면 활성화 코드는 없다")
        void usim_has_no_activation_code() {
            // given
            OrderNo usim = OrderNo.of("ORD-20261021-USIM0001");
            f.pickupReadyUsimOrder(usim);
            f.supplier.activationReturns(Iccid.of(IssuanceFixture.ICCID), SupplierProfileResult.unknown());
            f.usim.register(USER, usim, IssuanceFixture.ICCID);

            // when
            MySimDetail detail = service.detail(USER, usim);

            // then
            assertThat(detail.sim().iccid()).isEqualTo(IssuanceFixture.ICCID);
            assertThat(detail.sim().activationCode()).isNull();
        }

        @Test
        @DisplayName("남의 주문이면 404 ORDER_NOT_FOUND")
        void others_order() {
            // given
            f.issued();

            // when // then
            assertThatThrownBy(() -> service.detail(UserId.of("user-2"), ORDER_NO))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("발급 대기 정보는")
    class Waiting {

        @Test
        @DisplayName("공급사 요청 전(PAID)이면 QUEUED, 기다리기·나중에 확인·환불 요청이 가능")
        void queued() {
            // given
            f.paidEsimOrder();

            // when
            IssuanceWaiting waiting = service.detail(USER, ORDER_NO).waiting();

            // then
            assertThat(waiting.stage()).isEqualTo(Stage.QUEUED);
            assertThat(waiting.requestedAt()).isNull();
            assertThat(waiting.resultDeadline()).isNull();
            assertThat(waiting.pollIntervalSeconds()).isEqualTo(MySimQueryService.POLL_INTERVAL_SECONDS);
            assertThat(waiting.actions()).containsExactly(Action.WAIT, Action.CHECK_LATER, Action.REQUEST_REFUND);
        }

        @Test
        @DisplayName("공급사 처리 중이면 REQUESTED, 결과 대기 한도(요청 + 2분)를 주고 환불 요청은 없음")
        void requested() {
            // given
            f.dispatched();
            f.clock.advance(Duration.ofSeconds(30));

            // when
            IssuanceWaiting waiting = service.detail(USER, ORDER_NO).waiting();

            // then
            assertThat(waiting.stage()).isEqualTo(Stage.REQUESTED);
            assertThat(waiting.requestedAt()).isEqualTo(NOW);
            assertThat(waiting.resultDeadline()).isEqualTo(NOW.plus(IssuanceFixture.CALLBACK_TIMEOUT));
            assertThat(waiting.overdue()).isFalse();
            assertThat(waiting.actions()).containsExactly(Action.WAIT, Action.CHECK_LATER);
        }

        @Test
        @DisplayName("결과 대기 한도를 넘기면 overdue (서버가 곧 실패 처리 후 자동 환불)")
        void overdue() {
            // given
            f.dispatched();
            f.clock.advance(Duration.ofMinutes(3));

            // when // then
            assertThat(service.detail(USER, ORDER_NO).waiting().overdue()).isTrue();
        }

        @Test
        @DisplayName("발급이 끝났거나 유심 수령 대기면 없음")
        void none_after_issue_or_for_usim() {
            // given
            f.issued();
            OrderNo usim = OrderNo.of("ORD-20261021-USIM0001");
            f.pickupReadyUsimOrder(usim);

            // when // then
            assertThat(service.detail(USER, ORDER_NO).waiting()).isNull();
            assertThat(service.detail(USER, usim).waiting()).isNull();
        }
    }
}
