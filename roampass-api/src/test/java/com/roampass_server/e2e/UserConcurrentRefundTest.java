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

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.mysim.application.MyRefundService;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;
import com.roampass_server.support.IntegrationTest;

/**
 * 사용자 환불 버튼 연타: 발급 전(PAID) 주문에 같은 사용자의 환불 요청 8개 동시
 * 실제 PostgreSQL, 스레드마다 별도 트랜잭션 커밋
 */
@IntegrationTest
@DisplayName("동시성: 사용자 환불 요청 연타")
class UserConcurrentRefundTest {

    private static final int REQUESTS = 8;
    private static final UserId USER = UserId.of("user-refund-concurrency");

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private MyRefundService refundService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RefundAttemptRepository refundRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    /** 주문 → 결제 승인 → PAID(테스트 프로필은 스케줄러가 꺼져 있어 발급 요청 전 상태 유지) */
    private OrderNo paidOrder() {
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        OrderNo orderNo = orderService.createOrder(USER, 1L, travelDate).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        assertThat(orderRepository.findByOrderNo(orderNo).orElseThrow().status()).isEqualTo(OrderStatus.PAID);
        return orderNo;
    }

    @Test
    @DisplayName("환불 요청 8개 동시 → 1개만 접수, 환불 요청 1건, 사용자 환불 이력 1건")
    void only_one_refund_accepted() throws Exception {
        // given
        OrderNo orderNo = paidOrder();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> {
                try {
                    refundService.refund(USER, orderNo);
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
        assertThat(refundRepository.findByOrderNo(orderNo)).hasSize(1);
        assertThat(historyRepository.findByOrderNo(orderNo))
                .filteredOn(h -> h.actor().asString().equals("USER:" + USER.value()))
                .filteredOn(h -> "REFUNDING".equals(h.toStatus()))
                .hasSize(1);
    }
}
