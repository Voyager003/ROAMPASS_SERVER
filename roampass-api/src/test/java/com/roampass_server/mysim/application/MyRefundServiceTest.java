package com.roampass_server.mysim.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.IssuanceFixture;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.RefundStatus;

@DisplayName("사용자 환불 요청")
class MyRefundServiceTest {

    private static final UserId USER = UserId.of("user-1");
    private static final Iccid ICCID = Iccid.of(IssuanceFixture.ICCID);

    private final IssuanceFixture f = new IssuanceFixture();
    private final MyRefundService service = new MyRefundService(
            new MyRefundExecutor(f.orders, f.refundOpener, f.recorder, f.clock), f.orders, f.profiles, f.supplier,
            f.applier);

    @Nested
    @DisplayName("접수되는 경우")
    class Accepted {

        @Test
        @DisplayName("발급 전(PAID)이면 REFUNDING, 환불 요청 1건, 이력 주체는 사용자")
        void before_issue() {
            // given
            f.paidEsimOrder();

            // when
            MyRefundResult result = service.refund(USER, ORDER_NO);

            // then
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.refunds.findByOrderNo(ORDER_NO)).singleElement()
                    .satisfies(r -> assertThat(r.status()).isEqualTo(RefundStatus.PENDING));
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> h.toStatus().equals("REFUNDING"))
                    .singleElement()
                    .satisfies(h -> {
                        assertThat(h.actor().asString()).isEqualTo("USER:user-1");
                        assertThat(h.reason()).isEqualTo(MyRefundExecutor.REASON);
                    });
        }

        @Test
        @DisplayName("발급됐지만 공급사 재조회로도 설치 전이면 REFUNDING")
        void issued_not_installed() {
            // given
            f.issued();
            f.supplier.profileReturns(ICCID, SupplierProfileResult.found(SupplierProfileState.RELEASED, null, 0));

            // when
            MyRefundResult result = service.refund(USER, ORDER_NO);

            // then
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.supplier.profileQueries()).containsExactly(ICCID);
        }
    }

    @Nested
    @DisplayName("거절되는 경우")
    class Rejected {

        @Test
        @DisplayName("공급사 재조회에서 설치가 확인되면 409 SIM_NOT_REFUNDABLE, 설치 상태는 반영")
        void installed_on_refresh() {
            // given
            f.issued();
            f.supplier.profileReturns(ICCID, SupplierProfileResult.found(SupplierProfileState.INSTALLED, null, 0));

            // when // then
            assertThatThrownBy(() -> service.refund(USER, ORDER_NO))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code", "SIM_NOT_REFUNDABLE");
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUED);
            assertThat(f.profiles.findByOrderNo(ORDER_NO).orElseThrow().status()).isEqualTo(InstallStatus.INSTALLED);
            assertThat(f.refunds.findByOrderNo(ORDER_NO)).isEmpty();
        }

        @Test
        @DisplayName("공급사에 발급 요청 중이면 409 ORDER_NOT_REFUNDABLE")
        void issue_requested() {
            // given
            f.dispatched();

            // when // then
            assertThatThrownBy(() -> service.refund(USER, ORDER_NO))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code", "ORDER_NOT_REFUNDABLE");
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
        }

        @Test
        @DisplayName("이미 환불을 요청했으면 두 번째는 409, 환불 요청은 1건 그대로")
        void second_request() {
            // given
            f.paidEsimOrder();
            service.refund(USER, ORDER_NO);

            // when // then
            assertThatThrownBy(() -> service.refund(USER, ORDER_NO))
                    .isInstanceOf(ConflictException.class)
                    .hasFieldOrPropertyWithValue("code", "ORDER_NOT_REFUNDABLE");
            assertThat(f.refunds.findByOrderNo(ORDER_NO)).hasSize(1);
        }

        @Test
        @DisplayName("남의 주문이면 404, 공급사도 조회하지 않음")
        void others_order() {
            // given
            f.issued();

            // when // then
            assertThatThrownBy(() -> service.refund(UserId.of("user-2"), ORDER_NO))
                    .isInstanceOf(NotFoundException.class)
                    .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
            assertThat(f.supplier.profileQueries()).isEmpty();
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUED);
        }
    }
}
