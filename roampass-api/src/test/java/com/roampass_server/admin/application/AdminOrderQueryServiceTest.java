package com.roampass_server.admin.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.application.IssuanceFixture;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderSearchCondition;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.RefundStatus;

@DisplayName("어드민 주문 조회")
class AdminOrderQueryServiceTest {

    private final IssuanceFixture f = new IssuanceFixture();
    private final AdminOrderQueryService service = new AdminOrderQueryService(f.orders, f.payments, f.profiles,
            f.requests, f.refunds, f.histories, f.callbackLogs);

    private static OrderSearchCondition all() {
        return new OrderSearchCondition(null, null, null, null);
    }

    @Nested
    @DisplayName("목록은")
    class Search {

        @Test
        @DisplayName("행마다 최근 결제 상태와 설치 상태를 함께 보여 준다 (발급 전이면 설치 상태 없음)")
        void summarizes_payment_and_install() {
            // given
            f.issued();
            f.pickupReadyUsimOrder(OrderNo.of("ORD-20261021-USIM0001"));

            // when
            PageResult<AdminOrderSummary> page = service.search(all(), 0, 20);

            // then
            assertThat(page.totalElements()).isEqualTo(2);
            assertThat(page.content()).filteredOn(s -> s.orderNo().equals(ORDER_NO.value())).singleElement()
                    .satisfies(s -> {
                        assertThat(s.orderStatus()).isEqualTo(OrderStatus.ISSUED);
                        assertThat(s.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
                        assertThat(s.installStatus()).isEqualTo(InstallStatus.ISSUED);
                    });
            assertThat(page.content()).filteredOn(s -> s.orderNo().equals("ORD-20261021-USIM0001")).singleElement()
                    .satisfies(s -> {
                        assertThat(s.paymentStatus()).isNull();
                        assertThat(s.installStatus()).isNull();
                    });
        }

        @Test
        @DisplayName("주문 상태·SIM 종류로 거르고, 페이지 크기만큼만 돌려준다")
        void filters_and_pages() {
            // given
            f.issued();
            f.pickupReadyUsimOrder(OrderNo.of("ORD-20261021-USIM0001"));
            f.pickupReadyUsimOrder(OrderNo.of("ORD-20261021-USIM0002"));

            // when
            PageResult<AdminOrderSummary> usim = service.search(
                    new OrderSearchCondition(OrderStatus.PICKUP_READY, SimType.USIM, null, null), 0, 1);

            // then
            assertThat(usim.totalElements()).isEqualTo(2);
            assertThat(usim.content()).hasSize(1);
            assertThat(usim.content().getFirst().simType()).isEqualTo(SimType.USIM);
        }
    }

    @Nested
    @DisplayName("상세는")
    class Detail {

        @Test
        @DisplayName("결제·발급 요청·프로필·이력 타임라인·콜백 기록을 함께 돌려준다")
        void issued_order() {
            // given
            f.issued();

            // when
            AdminOrderDetail detail = service.detail(ORDER_NO);

            // then
            assertThat(detail.order().status()).isEqualTo(OrderStatus.ISSUED);
            assertThat(detail.payments()).singleElement()
                    .satisfies(p -> assertThat(p.status()).isEqualTo(PaymentStatus.APPROVED));
            assertThat(detail.issuanceRequests()).singleElement()
                    .satisfies(r -> assertThat(r.status()).isEqualTo(IssuanceStatus.COMPLETED));
            assertThat(detail.profile().iccid()).isEqualTo(IssuanceFixture.ICCID);
            assertThat(detail.profile().refundable()).isTrue();
            assertThat(detail.profile().expiresAt()).isNull(); // 개통 전
            assertThat(detail.refunds()).isEmpty();
            assertThat(detail.timeline()).extracting(AdminOrderDetail.TimelineEntry::toStatus)
                    .containsExactly("ISSUE_REQUESTED", "ISSUED", "ISSUED");
            assertThat(detail.timeline()).extracting(AdminOrderDetail.TimelineEntry::actor)
                    .containsExactly("SYSTEM", "SUPPLIER", "SUPPLIER");
            assertThat(detail.callbacks()).singleElement().satisfies(c -> {
                assertThat(c.eventKey()).isEqualTo("SUPPLIER:sevt_issued");
                assertThat(c.outcome()).isEqualTo(CallbackOutcome.PROCESSED);
            });
        }

        @Test
        @DisplayName("발급 실패 주문이면 환불 시도가 보인다")
        void failed_order_shows_refund() {
            // given
            String sup = f.dispatched();
            f.sendCallback(f.issueFailed("sevt_fail", sup));

            // when
            AdminOrderDetail detail = service.detail(ORDER_NO);

            // then
            assertThat(detail.order().status()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(detail.refunds()).singleElement()
                    .satisfies(r -> assertThat(r.status()).isEqualTo(RefundStatus.PENDING));
            assertThat(detail.profile()).isNull();
        }

        @Test
        @DisplayName("없는 주문이면 404 ORDER_NOT_FOUND")
        void not_found() {
            // when // then
            assertThatThrownBy(() -> service.detail(OrderNo.of("ORD-20261021-NONE0000")))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }
    }
}
