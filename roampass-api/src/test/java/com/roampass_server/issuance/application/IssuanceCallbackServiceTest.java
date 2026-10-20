package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ICCID;
import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.callback.application.InvalidSignatureException;
import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.service.SupplierIssueResult;
import com.roampass_server.order.domain.model.OrderStatus;

@DisplayName("공급사 발급 콜백")
class IssuanceCallbackServiceTest {

    private final IssuanceFixture f = new IssuanceFixture();

    @Nested
    @DisplayName("발급 완료 콜백이 오면")
    class Issued {

        @Test
        @DisplayName("프로필 생성(ICCID·활성화 코드·요금제 일수), 주문 ISSUED, 이력에 프로필 발급·발급 완료(SUPPLIER)")
        void attaches_profile() {
            // given
            String sup = f.dispatched();

            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_1", sup));

            // then
            assertThat(result).isEqualTo(CallbackResult.PROCESSED);
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUED);
            assertThat(f.profiles.findByOrderNo(ORDER_NO)).get().satisfies(profile -> {
                assertThat(profile.iccid().value()).isEqualTo(ICCID);
                assertThat(profile.status()).isEqualTo(InstallStatus.ISSUED);
                assertThat(profile.usageDays()).isEqualTo(5);
                assertThat(profile.supplierOrderNo()).isEqualTo(SupplierOrderNo.of(sup));
            });
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> !"ISSUE_REQUESTED".equals(h.toStatus()))
                    .extracting(h -> h.target().type(), StatusHistory::toStatus, StatusHistory::actor)
                    .containsExactly(
                            tuple(TargetType.SIM_PROFILE, "ISSUED", Actor.supplier()),
                            tuple(TargetType.ORDER, "ISSUED", Actor.supplier()));
        }

        @Test
        @DisplayName("접수 응답보다 콜백이 먼저 와도 처리되고, 늦게 온 접수 응답은 변화 없음")
        void callback_before_acceptance() {
            // given
            f.paidEsimOrder();
            f.supplier.willReturn(SupplierIssueResult.unknown("read timeout"));
            f.dispatch.dispatch();
            String sup = "SUP-" + ORDER_NO.value() + "-ISSUE-1";

            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_1", sup));
            f.supplier.willReturn(null);
            f.dispatch.dispatch(); // PENDING이 아니므로 재전송 대상 아님

            // then
            assertThat(result).isEqualTo(CallbackResult.PROCESSED);
            assertThat(f.requests.findLatestByOrderNo(ORDER_NO)).get()
                    .extracting(r -> r.status()).isEqualTo(IssuanceStatus.COMPLETED);
            assertThat(f.supplier.requests()).hasSize(1);
        }

        @Test
        @DisplayName("같은 eventId가 다시 오면 DUPLICATE, 프로필 1개")
        void duplicate_event() {
            // given
            String sup = f.dispatched();
            f.sendCallback(f.issued("sevt_1", sup));

            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_1", sup));

            // then
            assertThat(result).isEqualTo(CallbackResult.DUPLICATE);
            assertThat(f.profiles.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 eventId로 같은 발급 결과가 오면 ALREADY_APPLIED, 프로필 1개")
        void same_result_other_event() {
            // given
            String sup = f.dispatched();
            f.sendCallback(f.issued("sevt_1", sup));

            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_2", sup));

            // then
            assertThat(result).isEqualTo(CallbackResult.ALREADY_APPLIED);
            assertThat(f.profiles.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 실패(대기 시간 초과) 처리된 주문의 늦은 성공은 IGNORED(LATE_ISSUANCE), 프로필을 붙이지 않음")
        void late_issuance_is_ignored() {
            // given
            String sup = f.dispatched();
            f.clock.advance(IssuanceFixture.CALLBACK_TIMEOUT);
            f.timeout.failOverdue();

            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_1", sup));

            // then
            assertThat(result).isEqualTo(CallbackResult.IGNORED);
            assertThat(f.profiles.count()).isZero();
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.callbackLogs.findByEventKey("SUPPLIER:sevt_1")).get()
                    .satisfies(log -> {
                        assertThat(log.outcome()).isEqualTo(CallbackOutcome.IGNORED);
                        assertThat(log.note()).isEqualTo("LATE_ISSUANCE");
                    });
        }

        @Test
        @DisplayName("접수 번호가 다르면 IGNORED(SUPPLIER_ORDER_MISMATCH)")
        void supplier_order_mismatch() {
            // given
            f.dispatched();

            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_1", "SUP-OTHER"));

            // then
            assertThat(result).isEqualTo(CallbackResult.IGNORED);
            assertThat(f.profiles.count()).isZero();
        }
    }

    @Nested
    @DisplayName("그 밖의 콜백")
    class Others {

        @Test
        @DisplayName("발급 실패 콜백이면 ISSUE_FAILED → REFUNDING, 환불 요청 생성")
        void issue_failed() {
            // given
            String sup = f.dispatched();

            // when
            CallbackResult result = f.sendCallback(f.issueFailed("sevt_1", sup));

            // then
            assertThat(result).isEqualTo(CallbackResult.PROCESSED);
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.REFUNDING);
            assertThat(f.refunds.findByOrderNo(ORDER_NO)).hasSize(1);
        }

        @Test
        @DisplayName("발급 요청이 없는 주문이면 ORDER_NOT_FOUND로 기록")
        void unknown_order() {
            // when
            CallbackResult result = f.sendCallback(f.issued("sevt_1", "SUP-X"));

            // then
            assertThat(result).isEqualTo(CallbackResult.ORDER_NOT_FOUND);
            assertThat(f.callbackLogs.findByEventKey("SUPPLIER:sevt_1")).get()
                    .extracting(CallbackLog::outcome).isEqualTo(CallbackOutcome.TARGET_NOT_FOUND);
        }

        @Test
        @DisplayName("서명이 틀리면 401, 이벤트 키 없이 서명 실패로 기록하고 본문을 해석하지 않음")
        void invalid_signature() {
            // given
            f.dispatched();

            // when // then
            assertThatThrownBy(() -> f.callbacks.receive("body", "deadbeef", raw -> {
                throw new AssertionError("parser must not be called");
            })).isInstanceOf(InvalidSignatureException.class);
            assertThat(f.callbackLogs.all()).singleElement().satisfies(log -> {
                assertThat(log.eventKey()).isNull();
                assertThat(log.signatureOk()).isFalse();
            });
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUE_REQUESTED);
        }
    }
}
