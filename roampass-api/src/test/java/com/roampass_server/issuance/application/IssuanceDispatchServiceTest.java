package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;
import com.roampass_server.order.domain.model.OrderStatus;

@DisplayName("서버 주도 발급 요청")
class IssuanceDispatchServiceTest {

    private final IssuanceFixture f = new IssuanceFixture();

    @Test
    @DisplayName("PAID 이심 주문을 ISSUE_REQUESTED로 바꾸고 멱등 키로 공급사에 요청, 접수 번호 저장")
    void dispatches_paid_esim_order() {
        // given
        f.paidEsimOrder();

        // when
        int started = f.dispatch.dispatch();

        // then
        assertThat(started).isEqualTo(1);
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
        assertThat(f.supplier.requests()).singleElement()
                .satisfies(r -> assertThat(r.idempotencyKey()).isEqualTo("ORD-20261021-AAAA1111-ISSUE-1"));
        assertThat(f.requests.findLatestByOrderNo(ORDER_NO)).get().satisfies(r -> {
            assertThat(r.status()).isEqualTo(IssuanceStatus.ACCEPTED);
            assertThat(r.supplierOrderNo()).isEqualTo(SupplierOrderNo.of("SUP-ORD-20261021-AAAA1111-ISSUE-1"));
        });
        assertThat(f.histories.findByOrderNo(ORDER_NO))
                .extracting(StatusHistory::fromStatus, StatusHistory::toStatus, StatusHistory::actor)
                .containsExactly(tuple("PAID", "ISSUE_REQUESTED", Actor.system()));
    }

    @Test
    @DisplayName("접수 결과를 모르면 PENDING으로 두고 다음 실행에 같은 멱등 키로 다시 보낸다")
    void resends_unknown_with_same_key() {
        // given
        f.paidEsimOrder();
        f.supplier.willReturn(SupplierIssueResult.unknown("read timeout"));
        f.dispatch.dispatch();
        f.supplier.willReturn(null);

        // when
        int started = f.dispatch.dispatch();

        // then
        assertThat(started).isZero();
        assertThat(f.supplier.requests()).extracting(r -> r.idempotencyKey())
                .containsExactly("ORD-20261021-AAAA1111-ISSUE-1", "ORD-20261021-AAAA1111-ISSUE-1");
        assertThat(f.requests.findLatestByOrderNo(ORDER_NO)).get()
                .extracting(r -> r.status()).isEqualTo(IssuanceStatus.ACCEPTED);
    }

    @Test
    @DisplayName("공급사가 접수를 거절하면 ISSUE_FAILED를 거쳐 REFUNDING, 환불 요청 생성")
    void rejected_request_starts_refund() {
        // given
        f.paidEsimOrder();
        f.supplier.willReturn(SupplierIssueResult.rejected("PG_REJECTED_400"));

        // when
        f.dispatch.dispatch();

        // then
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
        assertThat(f.requests.findLatestByOrderNo(ORDER_NO)).get()
                .extracting(r -> r.status()).isEqualTo(IssuanceStatus.FAILED);
        assertThat(f.refunds.findByOrderNo(ORDER_NO)).hasSize(1);
    }
}
