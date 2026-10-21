package com.roampass_server.admin.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.model.CallbackSource;
import com.roampass_server.issuance.application.IssuanceFixture;

@DisplayName("어드민 이상 건")
class AdminIssueQueryServiceTest {

    private static final Duration STUCK = Duration.ofMinutes(5);

    private final IssuanceFixture f = new IssuanceFixture();
    private final AdminIssueQueryService service = new AdminIssueQueryService(f.orders, f.payments, f.callbackLogs,
            new AdminOrderQueryService(f.orders, f.payments, f.profiles, f.requests, f.refunds, f.histories,
                    f.callbackLogs), f.clock, STUCK, 50);

    @Test
    @DisplayName("결제 승인 후 5분이 지나도록 발급되지 않은 주문이 '결제 후 미발급'에 나온다 (5분 전에는 안 나옴)")
    void stuck_after_payment() {
        // given
        f.dispatched(); // ISSUE_REQUESTED, 결제 승인 시각 NOW

        // when
        f.clock.advance(STUCK.minusSeconds(1));
        AdminIssues before = service.find();
        f.clock.advance(Duration.ofSeconds(1));
        AdminIssues after = service.find();

        // then
        assertThat(before.stuckAfterPayment()).isEmpty();
        assertThat(after.stuckAfterPayment()).extracting(AdminOrderSummary::orderNo).containsExactly(ORDER_NO.value());
        assertThat(after.stuckThresholdMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("자동 환불을 포기한 주문은 NEEDS_ATTENTION 묶음에 나온다")
    void needs_attention() {
        // given
        f.needsAttention();

        // when
        AdminIssues issues = service.find();

        // then
        assertThat(issues.needsAttention()).extracting(AdminOrderSummary::orderNo).containsExactly(ORDER_NO.value());
    }

    @Test
    @DisplayName("금액 불일치 통지와 서명 실패 통지가 각각 나온다")
    void callback_anomalies() {
        // given
        f.callbackLogs.insertIfAbsent(CallbackLog.received(CallbackSource.PG, "PG:evt_mm", ORDER_NO, "{}",
                IssuanceFixture.NOW));
        f.callbackLogs.updateOutcome("PG:evt_mm", CallbackOutcome.AMOUNT_MISMATCH, "AMOUNT_MISMATCH");
        f.callbackLogs.save(CallbackLog.signatureRejected(CallbackSource.SUPPLIER, "forged", IssuanceFixture.NOW));

        // when
        AdminIssues issues = service.find();

        // then
        assertThat(issues.amountMismatches()).extracting(AdminOrderDetail.CallbackInfo::eventKey)
                .containsExactly("PG:evt_mm");
        assertThat(issues.signatureFailures()).singleElement()
                .satisfies(c -> assertThat(c.signatureOk()).isFalse());
    }
}
