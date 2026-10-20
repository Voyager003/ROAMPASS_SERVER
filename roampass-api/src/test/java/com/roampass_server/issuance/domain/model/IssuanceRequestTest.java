package com.roampass_server.issuance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;

@DisplayName("발급 요청")
class IssuanceRequestTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261021-AAAA1111");
    private static final SupplierOrderNo SUP = SupplierOrderNo.of("SUP-1");

    private static IssuanceRequest started() {
        return IssuanceRequest.start(ORDER_NO, 1, NOW);
    }

    @Test
    @DisplayName("시작하면 PENDING, 멱등 키는 {주문번호}-ISSUE-{시도 번호}")
    void starts_pending() {
        // when
        IssuanceRequest request = started();

        // then
        assertThat(request.status()).isEqualTo(IssuanceStatus.PENDING);
        assertThat(request.idempotencyKey()).isEqualTo("ORD-20261021-AAAA1111-ISSUE-1");
    }

    @Nested
    @DisplayName("접수되면")
    class Accept {

        @Test
        @DisplayName("ACCEPTED와 접수 번호, 같은 번호가 다시 와도 변화 없음")
        void accepts_once() {
            // when
            IssuanceRequest accepted = started().accept(SUP);

            // then
            assertThat(accepted.status()).isEqualTo(IssuanceStatus.ACCEPTED);
            assertThat(accepted.supplierOrderNo()).isEqualTo(SUP);
            assertThat(accepted.accept(SUP)).isSameAs(accepted);
        }

        @Test
        @DisplayName("콜백이 먼저 와서 끝난 요청에 접수 응답이 와도 변화 없음")
        void accept_after_completion_is_noop() {
            // given
            IssuanceRequest completed = started().complete(SUP, NOW);

            // when // then
            assertThat(completed.accept(SUP)).isSameAs(completed);
        }

        @Test
        @DisplayName("다른 접수 번호면 SUPPLIER_ORDER_MISMATCH")
        void rejects_other_number() {
            // when // then
            assertThatThrownBy(() -> started().accept(SUP).accept(SupplierOrderNo.of("SUP-2")))
                    .isInstanceOfSatisfying(InvalidValueException.class,
                            e -> assertThat(e.getCode()).isEqualTo("SUPPLIER_ORDER_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("완료·실패는")
    class CompleteAndFail {

        @Test
        @DisplayName("접수 응답 전(PENDING)이어도 완료할 수 있다")
        void completes_from_pending() {
            // when
            IssuanceRequest completed = started().complete(SUP, NOW);

            // then
            assertThat(completed.status()).isEqualTo(IssuanceStatus.COMPLETED);
            assertThat(completed.supplierOrderNo()).isEqualTo(SUP);
        }

        @Test
        @DisplayName("실패한 요청은 완료할 수 없고, 끝난 요청은 다시 실패시킬 수 없다")
        void closed_request_is_final() {
            // given
            IssuanceRequest failed = started().fail("timeout", NOW);

            // when // then
            assertThat(failed.status()).isEqualTo(IssuanceStatus.FAILED);
            assertThatThrownBy(() -> failed.complete(SUP, NOW)).isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(() -> started().complete(SUP, NOW).fail("x", NOW))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("요청 후 대기 시간이 지나도록 열려 있으면 지연(overdue)")
        void overdue() {
            // given
            IssuanceRequest request = started();
            Duration timeout = Duration.ofMinutes(2);

            // when // then
            assertThat(request.isOverdue(NOW.plus(timeout).minusSeconds(1), timeout)).isFalse();
            assertThat(request.isOverdue(NOW.plus(timeout), timeout)).isTrue();
            assertThat(request.complete(SUP, NOW).isOverdue(NOW.plus(timeout), timeout)).isFalse();
        }
    }
}
