package com.roampass_server.admin.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ICCID;
import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.issuance.application.IssuanceFixture;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.payment.domain.model.RefundStatus;

@DisplayName("운영자 조치")
class AdminActionServiceTest {

    private static final AdminId ADMIN = AdminId.of("admin-1");

    private final IssuanceFixture f = new IssuanceFixture();
    private final AdminActionService service = new AdminActionService(
            new AdminActionExecutor(f.orders, f.requests, f.refundOpener, f.recorder, f.clock), f.profiles,
            f.supplier, f.applier);

    @Nested
    @DisplayName("수동 환불은")
    class Refund {

        @Test
        @DisplayName("NEEDS_ATTENTION 주문을 REFUNDING으로 바꾸고 새 환불 요청을 만들며, 이력에 운영자·사유가 남는다")
        void refunds_needs_attention() {
            // given
            f.needsAttention();

            // when
            AdminActionResult result = service.refund(ADMIN, ORDER_NO, "고객 요청으로 수동 환불");

            // then
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.refunds.findByOrderNo(ORDER_NO)).extracting(r -> r.status())
                    .containsExactly(RefundStatus.GAVE_UP, RefundStatus.PENDING);
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> h.actor().asString().equals("ADMIN:admin-1"))
                    .extracting(StatusHistory::fromStatus, StatusHistory::toStatus, StatusHistory::reason)
                    .contains(tuple("NEEDS_ATTENTION", "REFUNDING", "고객 요청으로 수동 환불"));
        }

        @Test
        @DisplayName("사유가 없으면 400 ADMIN_REASON_REQUIRED, 상태 변화 없음")
        void requires_reason() {
            // given
            f.needsAttention();

            // when // then
            assertThatThrownBy(() -> service.refund(ADMIN, ORDER_NO, " "))
                    .isInstanceOfSatisfying(InvalidValueException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ADMIN_REASON_REQUIRED"));
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.NEEDS_ATTENTION);
        }

        @Test
        @DisplayName("발급된 주문은 공급사 상태를 다시 조회해, 개통됐으면 409 SIM_NOT_REFUNDABLE (프로필은 ACTIVE로 갱신)")
        void rechecks_supplier_before_refund() {
            // given
            f.issued();
            f.supplier.profileReturns(Iccid.of(ICCID),
                    SupplierProfileResult.found(SupplierProfileState.ENABLED, IssuanceFixture.NOW, 0));

            // when // then
            assertThatThrownBy(() -> service.refund(ADMIN, ORDER_NO, "환불 요청"))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("SIM_NOT_REFUNDABLE"));
            assertThat(f.profiles.findByOrderNo(ORDER_NO).orElseThrow().status()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUED);
        }

        @Test
        @DisplayName("발급됐지만 아직 설치 전이면 REFUNDING")
        void refunds_unused_sim() {
            // given
            f.issued();
            f.supplier.profileReturns(Iccid.of(ICCID),
                    SupplierProfileResult.found(SupplierProfileState.RELEASED, null, 0));

            // when // then
            assertThat(service.refund(ADMIN, ORDER_NO, "미사용 환불").orderStatus()).isEqualTo(OrderStatus.REFUNDING);
        }

        @Test
        @DisplayName("발급 진행 중(ISSUE_REQUESTED)이면 409 ORDER_NOT_REFUNDABLE")
        void rejects_in_flight() {
            // given
            f.dispatched();

            // when // then
            assertThatThrownBy(() -> service.refund(ADMIN, ORDER_NO, "환불 요청"))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_REFUNDABLE"));
        }
    }

    @Nested
    @DisplayName("재발급은")
    class Reissue {

        @Test
        @DisplayName("한 번도 발급된 적 없는 NEEDS_ATTENTION 이심 주문을 ISSUE_REQUESTED로, 새 발급 요청은 다음 시도 키로 전송")
        void reissues() {
            // given
            f.needsAttention();

            // when
            AdminActionResult result = service.reissue(ADMIN, ORDER_NO, "공급사 장애 복구 후 재발급");
            f.dispatch.dispatch();

            // then
            assertThat(result.orderStatus()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
            assertThat(f.requests.findByOrderNo(ORDER_NO)).extracting(r -> r.status())
                    .containsExactly(IssuanceStatus.FAILED, IssuanceStatus.ACCEPTED);
            assertThat(f.supplier.requests()).extracting(r -> r.idempotencyKey())
                    .endsWith("ORD-20261021-AAAA1111-ISSUE-2");
        }

        @Test
        @DisplayName("NEEDS_ATTENTION이 아니면 409 ORDER_NOT_REISSUABLE")
        void rejects_other_status() {
            // given
            f.issued();

            // when // then
            assertThatThrownBy(() -> service.reissue(ADMIN, ORDER_NO, "재발급"))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_REISSUABLE"));
        }
    }
}
