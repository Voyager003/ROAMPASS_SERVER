package com.roampass_server.e2e;

import static com.roampass_server.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.application.CallbackResult;
import com.roampass_server.issuance.application.IssuanceAcceptanceService;
import com.roampass_server.issuance.application.IssuanceCallbackCommand;
import com.roampass_server.issuance.application.IssuanceCallbackService;
import com.roampass_server.issuance.application.IssuanceStartService;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.support.IntegrationTest;

/**
 * 과제 필수 테스트: 같은 공급사 콜백 N개를 동시에 보내도 한 주문에 프로필 1개
 * 실제 PostgreSQL, 스레드마다 별도 트랜잭션 커밋(테스트 트랜잭션 롤백 없음)
 * 주문번호·ICCID가 매번 새로 생성되어 테스트끼리 데이터가 섞이지 않음
 */
@IntegrationTest
@DisplayName("동시성: 중복 공급사 콜백")
class IssuanceDuplicateCallbackConcurrencyTest {

    private static final int THREADS = 16;
    private static final UserId USER = UserId.of("callback-concurrency-user");

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
    private OrderRepository orderRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${roampass.issuance.callback-secret}")
    private String callbackSecret;

    private record Issuance(OrderNo orderNo, String supplierOrderNo) {
    }

    /** 결제 승인 → PAID → 발급 요청·접수까지 진행한 이심 주문 */
    private Issuance acceptedIssuance() {
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        OrderNo orderNo = orderService.createOrder(USER, 1L, travelDate).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        issuanceStartService.start(orderRepository.findByOrderNo(orderNo).orElseThrow()).orElseThrow();
        String supplierOrderNo = "SUP-" + orderNo.value();
        acceptanceService.accept(orderNo, SupplierOrderNo.of(supplierOrderNo));
        return new Issuance(orderNo, supplierOrderNo);
    }

    private CallbackResult sendIssued(Issuance issuance, String eventId, String iccid) {
        String body = """
                {"eventId":"%s","eventType":"ISSUED","supplierOrderNo":"%s","orderNo":"%s","iccid":"%s",
                 "activationCode":"LPA:1$smdp.example$X1Y2"}
                """.formatted(eventId, issuance.supplierOrderNo(), issuance.orderNo().value(), iccid);
        String signature = new HmacSignature(callbackSecret).sign(body);
        IssuanceCallbackCommand command = new IssuanceCallbackCommand(eventId, "ISSUED", issuance.supplierOrderNo(),
                issuance.orderNo().value(), iccid, "LPA:1$smdp.example$X1Y2", null);
        return callbackService.receive(body, signature, raw -> command);
    }

    private void assertOneProfile(Issuance issuance) {
        assertThat(jdbcTemplate.queryForObject("select count(*) from sim_profile where order_no = ?", Integer.class,
                issuance.orderNo().value())).isEqualTo(1);
        assertThat(orderRepository.findByOrderNo(issuance.orderNo()).orElseThrow().status())
                .isEqualTo(OrderStatus.ISSUED);
        assertThat(historyRepository.findByOrderNo(issuance.orderNo()))
                .filteredOn(h -> h.target().type() == TargetType.ORDER && "ISSUED".equals(h.toStatus()))
                .hasSize(1);
        assertThat(historyRepository.findByOrderNo(issuance.orderNo()))
                .filteredOn(h -> h.target().type() == TargetType.SIM_PROFILE)
                .hasSize(1);
    }

    @Test
    @DisplayName("같은 발급 완료 콜백(같은 eventId) 16개 동시 → 1개 PROCESSED·15개 DUPLICATE, sim_profile 1개·주문 ISSUED 이력 1건, 수신 기록 1건")
    void same_callback_sent_concurrently() throws Exception {
        // given
        Issuance issuance = acceptedIssuance();
        String eventId = "sevt_same_" + issuance.orderNo().value();
        String iccid = randomIccid();
        List<Callable<CallbackResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> sendIssued(issuance, eventId, iccid));
        }

        // when
        List<CallbackResult> results = runConcurrently(tasks);

        // then
        Map<CallbackResult, Long> counts = results.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(counts).containsEntry(CallbackResult.PROCESSED, 1L)
                .containsEntry(CallbackResult.DUPLICATE, (long) THREADS - 1);
        assertOneProfile(issuance);
        assertThat(jdbcTemplate.queryForObject("select count(*) from callback_log where event_key = ?",
                Integer.class, "SUPPLIER:" + eventId)).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 eventId로 같은 발급 결과 16개 동시 → 1개 PROCESSED·15개 ALREADY_APPLIED, sim_profile 1개")
    void distinct_events_same_result_concurrently() throws Exception {
        // given
        Issuance issuance = acceptedIssuance();
        String iccid = randomIccid();
        List<Callable<CallbackResult>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            String eventId = "sevt_" + i + "_" + issuance.orderNo().value();
            tasks.add(() -> sendIssued(issuance, eventId, iccid));
        }

        // when
        List<CallbackResult> results = runConcurrently(tasks);

        // then
        Map<CallbackResult, Long> counts = results.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(counts).containsEntry(CallbackResult.PROCESSED, 1L)
                .containsEntry(CallbackResult.ALREADY_APPLIED, (long) THREADS - 1);
        assertOneProfile(issuance);
    }

    /** 89 + 랜덤 16자리 + Luhn 체크 디지트(19자리), 테스트마다 달라 ICCID 유니크 제약과 충돌하지 않음 */
    private static String randomIccid() {
        StringBuilder digits = new StringBuilder("89");
        for (int i = 0; i < 16; i++) {
            digits.append(ThreadLocalRandom.current().nextInt(10));
        }
        int sum = 0;
        boolean doubleIt = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return digits.append((10 - sum % 10) % 10).toString();
    }
}
