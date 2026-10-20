package com.roampass_server.e2e;

import static com.roampass_server.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.roampass_server.admin.application.AdminActionService;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.application.IssuanceFailureService;
import com.roampass_server.issuance.application.IssuanceStartService;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.application.RefundResultService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.model.RefundStatus;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;
import com.roampass_server.support.IntegrationTest;

/**
 * 과제 2.3-⑤ "두 운영자가 동시에 눌러도 한 번만 실행": NEEDS_ATTENTION 주문에 수동 환불 8개 동시
 * 실제 PostgreSQL, 스레드마다 별도 트랜잭션 커밋
 */
@IntegrationTest
@DisplayName("동시성: 운영자 수동 환불")
class AdminConcurrentRefundTest {

    private static final int ADMINS = 8;
    private static final UserId USER = UserId.of("admin-concurrency-user");

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private IssuanceStartService issuanceStartService;

    @Autowired
    private IssuanceFailureService issuanceFailureService;

    @Autowired
    private RefundResultService refundResultService;

    @Autowired
    private RefundAttemptRepository refundRepository;

    @Autowired
    private AdminActionService actionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    /** 결제 → 발급 요청 → 발급 실패 → 자동 환불 3회 실패 → NEEDS_ATTENTION */
    private OrderNo needsAttentionOrder() {
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        OrderNo orderNo = orderService.createOrder(USER, 1L, travelDate).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        issuanceStartService.start(orderRepository.findByOrderNo(orderNo).orElseThrow()).orElseThrow();
        issuanceFailureService.fail(orderNo, "테스트용 발급 실패", Actor.system());
        for (int i = 0; i < 3; i++) {
            refundResultService.applyFailure(refundRepository.findByOrderNo(orderNo).getLast(), "PG down");
        }
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status())
                .isEqualTo(OrderStatus.NEEDS_ATTENTION);
        return orderNo;
    }

    @Test
    @DisplayName("운영자 8명이 동시에 수동 환불 → 1명만 성공, 새 환불 요청 1건, 운영자 환불 이력 1건")
    void only_one_admin_refund_runs() throws Exception {
        // given
        OrderNo orderNo = needsAttentionOrder();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < ADMINS; i++) {
            AdminId admin = AdminId.of("admin-" + i);
            tasks.add(() -> {
                try {
                    actionService.refund(admin, orderNo, "동시 클릭 테스트");
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            });
        }

        // when
        List<Boolean> results = runConcurrently(tasks);

        // then
        assertThat(results).containsOnlyOnce(true);
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status()).isEqualTo(OrderStatus.REFUNDING);
        assertThat(refundRepository.findByOrderNo(orderNo)).extracting(r -> r.status())
                .containsExactly(RefundStatus.GAVE_UP, RefundStatus.PENDING);
        assertThat(historyRepository.findByOrderNo(orderNo))
                .filteredOn(h -> h.actor().asString().startsWith("ADMIN:"))
                .hasSize(2); // 주문 REFUNDING 1건 + 환불 요청 생성 1건(같은 운영자)
    }
}
