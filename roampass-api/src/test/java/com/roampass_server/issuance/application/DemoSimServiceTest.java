package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.application.exception.UpstreamUnavailableException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.domain.model.OrderStatus;

@DisplayName("시연용 설치·개통·유심 수령")
class DemoSimServiceTest {

    private static final UserId USER = UserId.of("user-1");
    private static final OrderNo USIM_ORDER = OrderNo.of("ORD-20261021-USIM0001");

    private final IssuanceFixture f = new IssuanceFixture();
    private final FakeSupplierDemoClient demo = new FakeSupplierDemoClient();
    private final DemoSimService service = new DemoSimService(f.orders, f.profiles, demo, f.applier, f.clock);

    private InstallStatus status() {
        return f.profiles.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    @Nested
    @DisplayName("이심 설치·개통")
    class Esim {

        @Test
        @DisplayName("설치: 공급사 INSTALLED로 바꾸고 즉시 반영, 이력 주체 SUPPLIER·사유에 시연 표시")
        void install() {
            // given
            f.issued();

            // when
            InstallStatus result = service.install(USER, ORDER_NO);

            // then
            assertThat(result).isEqualTo(InstallStatus.INSTALLED);
            assertThat(status()).isEqualTo(InstallStatus.INSTALLED);
            assertThat(demo.stateRequests()).containsExactly(SupplierProfileState.INSTALLED);
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> "INSTALLED".equals(h.toStatus()))
                    .singleElement()
                    .satisfies(h -> {
                        assertThat(h.actor()).isEqualTo(Actor.supplier());
                        assertThat(h.reason()).isEqualTo(DemoSimService.SOURCE_DEMO + ": INSTALLED");
                    });
        }

        @Test
        @DisplayName("개통: 설치 후 ENABLED → ACTIVE, 설치를 건너뛰어도 개통 가능")
        void activate() {
            // given
            f.issued();
            service.install(USER, ORDER_NO);

            // when
            InstallStatus result = service.activate(USER, ORDER_NO);

            // then
            assertThat(result).isEqualTo(InstallStatus.ACTIVE);
            assertThat(f.profiles.findByOrderNo(ORDER_NO).orElseThrow().activatedAt()).isEqualTo(IssuanceFixture.NOW);
        }

        @Test
        @DisplayName("이미 설치된 SIM을 다시 설치, 개통된 SIM을 다시 개통하면 409 DEMO_NOT_APPLICABLE(공급사 호출 없음)")
        void not_applicable_after_progress() {
            // given
            f.issued();
            service.activate(USER, ORDER_NO);
            int calls = demo.stateRequests().size();

            // when // then
            assertThatThrownBy(() -> service.install(USER, ORDER_NO))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("DEMO_NOT_APPLICABLE"));
            assertThatThrownBy(() -> service.activate(USER, ORDER_NO))
                    .isInstanceOf(ConflictException.class);
            assertThat(demo.stateRequests()).hasSize(calls);
        }

        @Test
        @DisplayName("환불 중인 주문의 SIM은 409 DEMO_NOT_APPLICABLE(공급사 호출 없음)")
        void not_applicable_when_refunding() {
            // given
            f.issued();
            f.orders.saveIfStatus(f.orders.findByOrderNo(ORDER_NO).orElseThrow().startRefund(true), OrderStatus.ISSUED);
            assertThat(f.orderStatus()).isEqualTo(OrderStatus.REFUNDING);

            // when // then
            assertThatThrownBy(() -> service.install(USER, ORDER_NO))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("DEMO_NOT_APPLICABLE"));
            assertThat(demo.stateRequests()).isEmpty();
        }

        @Test
        @DisplayName("남의 주문은 404 ORDER_NOT_FOUND, 발급 전이면 404 SIM_NOT_FOUND")
        void not_found() {
            // given
            f.dispatched();

            // when // then
            assertThatThrownBy(() -> service.install(UserId.of("user-2"), ORDER_NO))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND"));
            assertThatThrownBy(() -> service.install(USER, ORDER_NO))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("SIM_NOT_FOUND"));
        }

        @Test
        @DisplayName("공급사가 응답하지 않으면 503 SUPPLIER_UNAVAILABLE, 상태는 그대로")
        void supplier_unavailable() {
            // given
            f.issued();
            demo.unavailable();

            // when // then
            assertThatThrownBy(() -> service.install(USER, ORDER_NO))
                    .isInstanceOfSatisfying(UpstreamUnavailableException.class,
                            e -> assertThat(e.getCode()).isEqualTo("SUPPLIER_UNAVAILABLE"));
            assertThat(status()).isEqualTo(InstallStatus.ISSUED);
        }
    }

    @Nested
    @DisplayName("유심 수령 카드")
    class PickupCard {

        @Test
        @DisplayName("수령 대기 유심 주문이면 공급사가 내준 카드 번호를 돌려줌(등록은 하지 않음)")
        void returns_card() {
            // given
            f.pickupReadyUsimOrder(USIM_ORDER);
            demo.pickupCardReturns("8982300900000000024");

            // when
            String iccid = service.pickupCard(USER, USIM_ORDER);

            // then
            assertThat(iccid).isEqualTo("8982300900000000024");
            assertThat(f.profiles.findByOrderNo(USIM_ORDER)).isEmpty();
        }

        @Test
        @DisplayName("이심 주문이나 이미 등록한 유심이면 409 DEMO_NOT_APPLICABLE, 남의 주문은 404")
        void not_applicable() {
            // given
            f.paidEsimOrder();
            f.pickupReadyUsimOrder(USIM_ORDER);

            // when // then
            assertThatThrownBy(() -> service.pickupCard(USER, ORDER_NO))
                    .isInstanceOfSatisfying(ConflictException.class,
                            e -> assertThat(e.getCode()).isEqualTo("DEMO_NOT_APPLICABLE"));
            assertThatThrownBy(() -> service.pickupCard(UserId.of("user-2"), USIM_ORDER))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("공급사가 응답하지 않으면 503 SUPPLIER_UNAVAILABLE")
        void supplier_unavailable() {
            // given
            f.pickupReadyUsimOrder(USIM_ORDER);
            demo.unavailable();

            // when // then
            assertThatThrownBy(() -> service.pickupCard(USER, USIM_ORDER))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }
    }
}
