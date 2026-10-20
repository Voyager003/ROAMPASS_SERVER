package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.CALLBACK_TIMEOUT;
import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.order.domain.model.OrderStatus;

@DisplayName("발급 콜백 대기 시간 초과")
class IssuanceTimeoutServiceTest {

    private final IssuanceFixture f = new IssuanceFixture();

    @Test
    @DisplayName("2분 안에 콜백이 없으면 ISSUE_FAILED → REFUNDING, 환불 요청 생성, 이력 흔적")
    void fails_overdue_and_starts_refund() {
        // given
        f.dispatched();
        f.clock.advance(CALLBACK_TIMEOUT);

        // when
        int failed = f.timeout.failOverdue();

        // then
        assertThat(failed).isEqualTo(1);
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
        assertThat(f.refunds.findByOrderNo(ORDER_NO)).singleElement()
                .satisfies(refund -> assertThat(refund.amount()).isEqualTo(IssuanceFixture.PRICE));
        assertThat(f.histories.findByOrderNo(ORDER_NO))
                .extracting(h -> h.target().type(), StatusHistory::fromStatus, StatusHistory::toStatus,
                        StatusHistory::actor)
                .containsExactly(
                        tuple(TargetType.ORDER, "PAID", "ISSUE_REQUESTED", Actor.system()),
                        tuple(TargetType.ORDER, "ISSUE_REQUESTED", "ISSUE_FAILED", Actor.system()),
                        tuple(TargetType.ORDER, "ISSUE_FAILED", "REFUNDING", Actor.system()),
                        tuple(TargetType.REFUND, null, "PENDING", Actor.system()));
    }

    @Test
    @DisplayName("2분이 되기 전이면 그대로 둔다")
    void keeps_within_timeout() {
        // given
        f.dispatched();
        f.clock.advance(CALLBACK_TIMEOUT.minusSeconds(1));

        // when // then
        assertThat(f.timeout.failOverdue()).isZero();
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
    }

    @Test
    @DisplayName("이미 발급 완료된 주문은 시간이 지나도 실패 처리하지 않는다")
    void ignores_completed() {
        // given
        String sup = f.dispatched();
        f.sendCallback(f.issued("sevt_1", sup));
        f.clock.advance(CALLBACK_TIMEOUT);

        // when // then
        assertThat(f.timeout.failOverdue()).isZero();
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUED);
    }
}
