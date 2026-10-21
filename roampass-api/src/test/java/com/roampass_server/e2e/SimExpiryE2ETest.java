package com.roampass_server.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
import com.roampass_server.issuance.application.FakeSupplierClient;
import com.roampass_server.issuance.application.IssuanceCallbackCommand;
import com.roampass_server.issuance.application.IssuanceCallbackService;
import com.roampass_server.issuance.application.IssuanceDispatchService;
import com.roampass_server.issuance.application.ProfileTrackingService;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.MutableClock;
import com.roampass_server.support.TestIccids;

/**
 * 만료일 테스트(부록 A 예시): 7일 요금제가 2026-05-01T15:20:00Z에 개통되면 KST 2026-05-09 00:20 만료
 * 주문 → 결제 → 발급 → 콜백 → 폴링으로 개통 반영까지 실제 유스케이스·DB, 시계와 공급사만 대역
 */
@IntegrationTest
@Import(SimExpiryE2ETest.Doubles.class)
@DisplayName("E2E: 개통 → KST 만료일")
class SimExpiryE2ETest {

    private static final Instant START = Instant.parse("2026-04-30T00:00:00Z");
    private static final UserId USER = UserId.of("expiry-user");
    private static final long US_ESIM_7_DAYS = 5L; // 시드 요금제 5번: US, 7일, 이심

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        FakeSupplierClient fakeSupplierClient() {
            return new FakeSupplierClient();
        }
    }

    @Autowired
    private MutableClock clock;

    @Autowired
    private FakeSupplierClient supplier;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRequestService paymentRequestService;

    @Autowired
    private PaymentConfirmationService confirmationService;

    @Autowired
    private IssuanceDispatchService dispatchService;

    @Autowired
    private IssuanceCallbackService callbackService;

    @Autowired
    private ProfileTrackingService trackingService;

    @Autowired
    private SimProfileRepository profileRepository;

    @Value("${roampass.issuance.callback-secret}")
    private String callbackSecret;

    @Test
    @DisplayName("2026-05-01T15:20Z 개통 신호가 폴링으로 반영되면 ACTIVE, 만료 2026-05-08T15:20Z = KST 2026-05-09 00:20")
    void expires_24h_times_days_after_activation() {
        // given: 7일 요금제 주문·결제·발급
        OrderNo orderNo = orderService.createOrder(USER, US_ESIM_7_DAYS, LocalDate.of(2026, 5, 1)).orderNo();
        Payment payment = paymentRequestService.start(USER, orderNo);
        confirmationService.confirmApproval(payment.idempotencyKey(), PgTid.of("pg_" + orderNo.value()),
                payment.amount(), Actor.pg());
        dispatchService.dispatch();
        String iccid = TestIccids.random();
        String supplierOrderNo = "SUP-" + orderNo.value() + "-ISSUE-1";
        String body = "issued-" + orderNo.value();
        callbackService.receive(body, new HmacSignature(callbackSecret).sign(body),
                raw -> new IssuanceCallbackCommand("sevt_exp_" + orderNo.value(), "ISSUED", supplierOrderNo,
                        orderNo.value(), iccid, "LPA:1$smdp.example$X1Y2", null));

        // when: 개통 뒤 폴링
        Instant activatedAt = Instant.parse("2026-05-01T15:20:00Z");
        clock.advance(Duration.between(START, Instant.parse("2026-05-01T16:00:00Z")));
        supplier.profileReturns(Iccid.of(iccid),
                SupplierProfileResult.found(SupplierProfileState.ENABLED, activatedAt, 0));
        trackingService.poll();

        // then
        SimProfile profile = profileRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(profile.status()).isEqualTo(InstallStatus.ACTIVE);
        assertThat(profile.activatedAt()).isEqualTo(activatedAt);
        assertThat(profile.usageDays()).isEqualTo(7);
        assertThat(profile.usagePeriod().expiresAt()).isEqualTo(Instant.parse("2026-05-08T15:20:00Z"));
        assertThat(profile.usagePeriod().expiresAtKst()).isEqualTo(LocalDateTime.of(2026, 5, 9, 0, 20));
        assertThat(profile.effectiveStatus(Instant.parse("2026-05-08T15:19:59Z"))).isEqualTo(InstallStatus.ACTIVE);
        assertThat(profile.effectiveStatus(Instant.parse("2026-05-08T15:20:00Z"))).isEqualTo(InstallStatus.EXPIRED);
    }
}
