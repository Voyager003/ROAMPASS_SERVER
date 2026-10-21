package com.roampass_server.admin.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static com.roampass_server.support.PersistenceTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.admin.application.AdminOrderSearch;
import com.roampass_server.admin.application.AdminOrderSearchCondition;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.issuance.domain.model.ActivationCode;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.model.Order;
import com.roampass_server.order.domain.model.OrderStatus;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.model.Payment;
import com.roampass_server.payment.domain.model.PaymentStatus;
import com.roampass_server.payment.domain.model.PgTid;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.support.IntegrationTest;
import com.roampass_server.support.TestIccids;

import jakarta.persistence.EntityManager;

/**
 * 어드민 주문 검색 SQL(PostgreSQL). 주문 3건:
 * A = 결제 승인 + 개통된 이심(5일), B = 결제 실패 후 재결제 대기(최근 시도 REQUESTED), C = 결제 전
 */
@IntegrationTest
@Transactional
@DisplayName("어드민 주문 검색 (PostgreSQL)")
class JdbcAdminOrderSearchIntegrationTest {

    private static final Money PRICE = Money.krw(9_900);

    @Autowired
    private AdminOrderSearch search;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimProfileRepository profileRepository;

    @Autowired
    private EntityManager entityManager;

    private void givenThreeOrders() {
        // A: 결제 승인 → 발급 → 개통
        Order a = orderRepository.save(pendingOrder("SRCHA001", NOW));
        orderRepository.saveIfStatus(a.confirmPayment(NOW), OrderStatus.PENDING);
        paymentRepository.save(Payment.request(a.orderNo(), 1, PRICE, NOW).approve(PgTid.of("pg_srch_a"), PRICE, NOW));
        profileRepository.save(SimProfile.issueEsim(a.orderNo(), SupplierOrderNo.of("SUP-SRCHA001"),
                Iccid.of(TestIccids.random()), ActivationCode.of("LPA:1$smdp.example$SRCH"), 5, NOW)
                .markActivated(NOW));
        // B: 1차 실패, 2차 결과 대기 → 최근 시도 REQUESTED
        Order b = orderRepository.save(pendingOrder("SRCHB001", NOW.plusSeconds(60)));
        paymentRepository.save(Payment.request(b.orderNo(), 1, PRICE, NOW.plusSeconds(60)).fail("declined", NOW.plusSeconds(61)));
        paymentRepository.save(Payment.request(b.orderNo(), 2, PRICE, NOW.plusSeconds(90)));
        // C: 결제 전
        orderRepository.save(pendingOrder("SRCHC001", NOW.plusSeconds(120)));
        entityManager.flush();
    }

    private PageResult<OrderNo> run(AdminOrderSearchCondition condition, Duration after) {
        return search.search(condition, NOW.plus(after), 0, 10);
    }

    @Test
    @DisplayName("조건이 없으면 전부 최신순")
    void all_newest_first() {
        // given
        givenThreeOrders();

        // when
        PageResult<OrderNo> page = run(AdminOrderSearchCondition.all(), Duration.ZERO);

        // then
        assertThat(page.content()).extracting(OrderNo::value)
                .containsExactly("ORD-20261020-SRCHC001", "ORD-20261020-SRCHB001", "ORD-20261020-SRCHA001");
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("결제 상태는 가장 최근 결제 시도 기준 (실패 후 재시도 중이면 REQUESTED, FAILED로는 안 걸림)")
    void payment_status_uses_latest_attempt() {
        // given
        givenThreeOrders();

        // when // then
        assertThat(run(new AdminOrderSearchCondition(null, PaymentStatus.REQUESTED, null, null, null, null), Duration.ZERO)
                .content()).extracting(OrderNo::value).containsExactly("ORD-20261020-SRCHB001");
        assertThat(run(new AdminOrderSearchCondition(null, PaymentStatus.FAILED, null, null, null, null), Duration.ZERO)
                .content()).isEmpty();
        assertThat(run(new AdminOrderSearchCondition(null, PaymentStatus.APPROVED, null, null, null, null), Duration.ZERO)
                .content()).extracting(OrderNo::value).containsExactly("ORD-20261020-SRCHA001");
    }

    @Test
    @DisplayName("설치 상태 ACTIVE/EXPIRED는 조회 시각 기준 (개통 + 5일 전후로 갈림)")
    void install_status_judged_at_now() {
        // given
        givenThreeOrders();
        AdminOrderSearchCondition active = new AdminOrderSearchCondition(null, null, InstallStatus.ACTIVE, null, null, null);
        AdminOrderSearchCondition expired = new AdminOrderSearchCondition(null, null, InstallStatus.EXPIRED, null, null, null);

        // when // then
        assertThat(run(active, Duration.ofDays(5).minusSeconds(1)).content()).hasSize(1);
        assertThat(run(expired, Duration.ofDays(5).minusSeconds(1)).content()).isEmpty();
        assertThat(run(active, Duration.ofDays(5)).content()).isEmpty();
        assertThat(run(expired, Duration.ofDays(5)).content()).extracting(OrderNo::value)
                .containsExactly("ORD-20261020-SRCHA001");
    }

    @Test
    @DisplayName("주문 상태·SIM 종류·생성 기간과 함께 쓰고, 페이지를 나눈다")
    void combines_with_order_filters_and_pages() {
        // given
        givenThreeOrders();

        // when
        PageResult<OrderNo> pending = search.search(new AdminOrderSearchCondition(OrderStatus.PENDING, null, null,
                SimType.ESIM, NOW, NOW.plusSeconds(3_600)), NOW, 0, 1);

        // then
        assertThat(pending.totalElements()).isEqualTo(2);
        assertThat(pending.content()).extracting(OrderNo::value).containsExactly("ORD-20261020-SRCHC001");
    }
}
