package com.roampass_server.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.application.IssuanceAcceptanceService;
import com.roampass_server.issuance.application.IssuanceCallbackCommand;
import com.roampass_server.issuance.application.IssuanceCallbackService;
import com.roampass_server.issuance.application.IssuanceStartService;
import com.roampass_server.issuance.application.IssuanceTimeoutService;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.FakePgClient;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.application.RefundExecutionService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;
import com.roampass_server.payment.domain.service.PgCancelResult;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.MutableClock;

/**
 * 과제 필수 테스트: "결제됐는데 발급 실패" → 결제가 그대로 남지 않고 자동 환불되거나 운영자 확인 대상이 됨
 * 실제 PostgreSQL·실제 유스케이스, 시계(MutableClock)와 PG 취소(Fake)만 대역
 */
@IntegrationTest
@Import(IssuanceFailureCompensationE2ETest.Doubles.class)
@DisplayName("E2E: 결제됐는데 발급 실패 → 자동 환불")
class IssuanceFailureCompensationE2ETest {

    private static final Instant START = Instant.parse("2026-10-21T00:00:00Z");
    private static final UserId USER = UserId.of("compensation-user");

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        FakePgClient fakePgClient() {
            return new FakePgClient();
        }
    }

    @Autowired
    private MutableClock clock;

    @Autowired
    private FakePgClient pg;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private IssuanceStartService issuanceStartService;

    @Autowired
    private IssuanceAcceptanceService acceptanceService;

    @Autowired
    private IssuanceCallbackService callbackService;

    @Autowired
    private IssuanceTimeoutService timeoutService;

    @Autowired
    private RefundExecutionService refundExecutionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundAttemptRepository refundRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Value("${roampass.issuance.callback-secret}")
    private String callbackSecret;

    @BeforeEach
    void resetPg() {
        pg.cancelReturns(PgCancelResult.cancelled());
    }

    /** 결제 승인(PAID) → 발급 요청·접수(ISSUE_REQUESTED)까지 */
    private OrderNo paidAndIssueRequested() {
        OrderNo orderNo = orderService.createOrder(USER, 1L, LocalDate.of(2026, 10, 25)).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        issuanceStartService.start(orderRepository.findByOrderNo(orderNo).orElseThrow()).orElseThrow();
        acceptanceService.accept(orderNo, SupplierOrderNo.of("SUP-" + orderNo.value()));
        return orderNo;
    }

    private OrderStatus orderStatus(OrderNo orderNo) {
        return orderRepository.findByOrderNo(orderNo).orElseThrow().status();
    }

    @Test
    @DisplayName("공급사 실패 콜백 → ISSUE_FAILED → REFUNDING → PG 취소 → REFUNDED, 결제 CANCELLED, 이력에 전 과정이 남는다")
    void issue_failure_is_refunded() {
        // given
        OrderNo orderNo = paidAndIssueRequested();
        String body = """
                {"eventId":"sevt_fail_%s","eventType":"ISSUE_FAILED","supplierOrderNo":"SUP-%s","orderNo":"%s",
                 "reason":"OUT_OF_STOCK"}
                """.formatted(orderNo.value(), orderNo.value(), orderNo.value());
        IssuanceCallbackCommand command = new IssuanceCallbackCommand("sevt_fail_" + orderNo.value(), "ISSUE_FAILED",
                "SUP-" + orderNo.value(), orderNo.value(), null, null, "OUT_OF_STOCK");

        // when: 실패 콜백
        callbackService.receive(body, new HmacSignature(callbackSecret).sign(body), raw -> command);

        // then: 환불 요청이 생기고 주문은 REFUNDING
        assertThat(orderStatus(orderNo)).isEqualTo(OrderStatus.REFUNDING);
        assertThat(refundRepository.findByOrderNo(orderNo)).singleElement()
                .satisfies(refund -> assertThat(refund.status()).isEqualTo(RefundStatus.PENDING));

        // when: 환불 실행(PG 취소 성공)
        refundExecutionService.processDue();

        // then
        assertThat(orderStatus(orderNo)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(refundRepository.findByOrderNo(orderNo)).singleElement()
                .satisfies(refund -> assertThat(refund.status()).isEqualTo(RefundStatus.SUCCEEDED));
        assertThat(paymentRepository.findByOrderNo(orderNo)).singleElement()
                .satisfies(payment -> assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED));
        assertThat(historyRepository.findByOrderNo(orderNo))
                .extracting(h -> h.target().type().name(), StatusHistory::fromStatus, StatusHistory::toStatus,
                        StatusHistory::actor)
                .containsExactly(
                        tuple("ORDER", null, "PENDING", Actor.user(USER)),
                        tuple("PAYMENT", null, "REQUESTED", Actor.user(USER)),
                        tuple("PAYMENT", "REQUESTED", "APPROVED", Actor.pg()),
                        tuple("ORDER", "PENDING", "PAID", Actor.pg()),
                        tuple("ORDER", "PAID", "ISSUE_REQUESTED", Actor.system()),
                        tuple("ORDER", "ISSUE_REQUESTED", "ISSUE_FAILED", Actor.supplier()),
                        tuple("ORDER", "ISSUE_FAILED", "REFUNDING", Actor.system()),
                        tuple("REFUND", null, "PENDING", Actor.system()),
                        tuple("REFUND", "PENDING", "SUCCEEDED", Actor.system()),
                        tuple("PAYMENT", "APPROVED", "CANCELLED", Actor.system()),
                        tuple("ORDER", "REFUNDING", "REFUNDED", Actor.system()));
    }

    @Test
    @DisplayName("콜백 누락 → 2분 뒤 실패 처리·환불 시작 → PG 취소 3회 실패 → NEEDS_ATTENTION(운영자 확인 대상)")
    void missing_callback_then_refund_gives_up() {
        // given
        OrderNo orderNo = paidAndIssueRequested();
        pg.cancelReturns(PgCancelResult.unknown("read timeout"));

        // when: 콜백 없이 2분 경과
        clock.advance(Duration.ofMinutes(2));
        timeoutService.failOverdue();

        // then
        assertThat(orderStatus(orderNo)).isEqualTo(OrderStatus.REFUNDING);

        // when: 환불 3회 시도(즉시, 1분 뒤, 2분 뒤) 모두 실패
        refundExecutionService.processDue();
        clock.advance(Duration.ofMinutes(1));
        refundExecutionService.processDue();
        clock.advance(Duration.ofMinutes(2));
        refundExecutionService.processDue();

        // then: 결제는 그대로(취소 안 됨)지만 운영자가 바로 인지할 상태
        assertThat(orderStatus(orderNo)).isEqualTo(OrderStatus.NEEDS_ATTENTION);
        assertThat(refundRepository.findByOrderNo(orderNo)).singleElement().satisfies(refund -> {
            assertThat(refund.status()).isEqualTo(RefundStatus.GAVE_UP);
            assertThat(refund.attemptCount()).isEqualTo(3);
            assertThat(refund.lastError()).contains("read timeout");
        });
        assertThat(paymentRepository.findByOrderNo(orderNo)).singleElement()
                .satisfies(payment -> assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED));
        assertThat(historyRepository.findByOrderNo(orderNo))
                .filteredOn(h -> h.target().type().name().equals("ORDER"))
                .extracting(StatusHistory::toStatus)
                .endsWith("ISSUE_FAILED", "REFUNDING", "NEEDS_ATTENTION");
    }
}
