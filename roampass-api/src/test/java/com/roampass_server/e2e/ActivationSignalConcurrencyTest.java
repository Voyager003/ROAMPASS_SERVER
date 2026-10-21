package com.roampass_server.e2e;

import static com.roampass_server.support.ConcurrentRunner.runConcurrently;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.application.IssuanceAcceptanceService;
import com.roampass_server.issuance.application.IssuanceCallbackCommand;
import com.roampass_server.issuance.application.IssuanceCallbackService;
import com.roampass_server.issuance.application.IssuanceStartService;
import com.roampass_server.issuance.application.ProfileSignalApplier;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.TestIccids;

/**
 * 개통 신호 두 경로(개통 이벤트 콜백 + 상태 조회 폴링)가 같은 개통을 동시에 반영해도 한 번만 반영되는지
 * 실제 PostgreSQL, 스레드마다 별도 트랜잭션 커밋
 */
@IntegrationTest
@DisplayName("동시성: 개통 콜백과 폴링이 겹침")
class ActivationSignalConcurrencyTest {

    private static final UserId USER = UserId.of("activation-concurrency-user");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-10-25T15:20:00Z");

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
    private ProfileSignalApplier applier;

    @Autowired
    private SimProfileRepository profileRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StatusHistoryRepository historyRepository;

    @Value("${roampass.issuance.callback-secret}")
    private String callbackSecret;

    /** 결제 → 발급 요청·접수 → 발급 완료 콜백까지 진행해 프로필 ISSUED */
    private OrderNo issuedEsim(String iccid) {
        LocalDate travelDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(5);
        OrderNo orderNo = orderService.createOrder(USER, 1L, travelDate).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        issuanceStartService.start(orderRepository.findByOrderNo(orderNo).orElseThrow()).orElseThrow();
        String supplierOrderNo = "SUP-" + orderNo.value();
        acceptanceService.accept(orderNo, SupplierOrderNo.of(supplierOrderNo));
        send(new IssuanceCallbackCommand("sevt_issue_" + orderNo.value(), "ISSUED", supplierOrderNo, orderNo.value(),
                iccid, "LPA:1$smdp.example$X1Y2", null));
        return orderNo;
    }

    private void send(IssuanceCallbackCommand command) {
        String body = "callback-" + command.eventId(); // 서명 대상 원문(내용 무관, 파서는 명령을 그대로 돌려줌)
        callbackService.receive(body, new HmacSignature(callbackSecret).sign(body), raw -> command);
    }

    @Test
    @DisplayName("개통 콜백 4개(서로 다른 eventId) + 폴링 반영 4개 동시 → ACTIVE, 개통 시각은 공급사 값, 개통 이력 1건")
    void callback_and_polling_apply_once() throws Exception {
        // given
        String iccid = TestIccids.random();
        OrderNo orderNo = issuedEsim(iccid);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String eventId = "sevt_act_" + i + "_" + orderNo.value();
            tasks.add(() -> {
                send(new IssuanceCallbackCommand(eventId, "ACTIVATED", null, null, iccid, null, null,
                        ACTIVATED_AT.toString()));
                return true;
            });
            tasks.add(() -> applier.apply(profileRepository.findByOrderNo(orderNo).orElseThrow(),
                    SupplierProfileResult.found(SupplierProfileState.ENABLED, ACTIVATED_AT, 0)));
        }

        // when
        runConcurrently(tasks);

        // then
        SimProfile profile = profileRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(profile.status()).isEqualTo(InstallStatus.ACTIVE);
        assertThat(profile.activatedAt()).isEqualTo(ACTIVATED_AT);
        assertThat(historyRepository.findByOrderNo(orderNo))
                .filteredOn(h -> h.target().type() == TargetType.SIM_PROFILE && "ACTIVE".equals(h.toStatus()))
                .hasSize(1);
    }
}
