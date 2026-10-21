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
import org.springframework.jdbc.core.JdbcTemplate;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.application.UsimRegistrationStartService;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.TestIccids;

/**
 * 유심 ICCID 동시 등록: 같은 ICCID를 여러 주문이 동시에 등록해도 1건만, 같은 주문을 동시에 등록해도 1건만
 * 실제 PostgreSQL(유니크 제약·조건부 UPDATE), 공급사 개통 호출 전 단계(등록 트랜잭션)만 대상
 */
@IntegrationTest
@DisplayName("동시성: 유심 ICCID 등록")
class UsimRegistrationConcurrencyTest {

    private static final int THREADS = 8;
    private static final UserId USER = UserId.of("usim-concurrency-user");
    private static final long JP_USIM_5_DAYS = 3L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private UsimRegistrationStartService registrationService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private OrderNo pickupReadyUsimOrder() {
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        OrderNo orderNo = orderService.createOrder(USER, JP_USIM_5_DAYS, travelDate).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        return orderNo;
    }

    private Callable<Boolean> register(OrderNo orderNo, String iccid) {
        return () -> {
            try {
                registrationService.register(USER, orderNo, iccid);
                return true;
            } catch (RuntimeException e) { // 409(ConflictException) 또는 DB 유니크 위반 → 409
                return false;
            }
        };
    }

    @Test
    @DisplayName("주문 8개가 같은 ICCID를 동시에 등록 → 1건만 성공, 그 ICCID 프로필 1개, ISSUED 주문 1개")
    void same_iccid_on_many_orders() throws Exception {
        // given
        String iccid = TestIccids.random();
        List<OrderNo> orders = new ArrayList<>();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            OrderNo orderNo = pickupReadyUsimOrder();
            orders.add(orderNo);
            tasks.add(register(orderNo, iccid));
        }

        // when
        List<Boolean> results = runConcurrently(tasks);

        // then
        assertThat(results).containsOnlyOnce(true);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sim_profile where iccid = ?", Integer.class,
                iccid)).isEqualTo(1);
        assertThat(orders).filteredOn(o -> orderRepository.findByOrderNo(o).orElseThrow().status() == OrderStatus.ISSUED)
                .hasSize(1);
    }

    @Test
    @DisplayName("한 주문에 서로 다른 ICCID 8개를 동시에 등록 → 1건만 성공, 그 주문 프로필 1개")
    void many_iccids_on_one_order() throws Exception {
        // given
        OrderNo orderNo = pickupReadyUsimOrder();
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(register(orderNo, TestIccids.random()));
        }

        // when
        List<Boolean> results = runConcurrently(tasks);

        // then
        assertThat(results).containsOnlyOnce(true);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sim_profile where order_no = ?", Integer.class,
                orderNo.value())).isEqualTo(1);
    }
}
