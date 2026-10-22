package com.roampass.mock.supplier.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass.mock.supplier.callback.CallbackDelivery;
import com.roampass.mock.supplier.callback.CallbackEventType;
import com.roampass.mock.supplier.callback.CallbackPublisher;
import com.roampass.mock.supplier.fault.FaultConfig;
import com.roampass.mock.supplier.fault.FaultSettings;

@DisplayName("mock-supplier 발급 처리")
class MockSupplierServiceTest {

    private static final IssueCommand REQUEST = new IssueCommand("ORD-20261020-AAAA1111-1", "ORD-20261020-AAAA1111");
    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");

    private final List<CallbackDelivery> published = new CopyOnWriteArrayList<>();
    private final CallbackPublisher publisher = published::add;
    private double nextRandom = 0.5;
    private final FaultConfig faults = new FaultConfig(FaultSettings.none(), () -> nextRandom);
    private final MockSupplierService service = new MockSupplierService(publisher, faults, new IccidGenerator());

    @Nested
    @DisplayName("발급 요청이 오면")
    class Issue {

        @Test
        @DisplayName("접수 번호를 돌려주고, ICCID·활성화 코드가 담긴 발급 완료 콜백을 1건 발행한다")
        void accepts_and_publishes_issued_callback() {
            // when
            IssueResult result = service.issue(REQUEST);

            // then
            assertThat(result.supplierOrderNo()).startsWith("SUP-");
            assertThat(published).singleElement().satisfies(delivery -> {
                assertThat(delivery.signatureValid()).isTrue();
                var event = delivery.event();
                assertThat(event.eventId()).startsWith("sevt_");
                assertThat(event.eventType()).isEqualTo(CallbackEventType.ISSUED);
                assertThat(event.supplierOrderNo()).isEqualTo(result.supplierOrderNo());
                assertThat(event.orderNo()).isEqualTo(REQUEST.orderNo());
                assertThat(event.iccid()).hasSize(19).containsOnlyDigits();
                assertThat(event.activationCode()).matches("LPA:1\\$[^$\\s]+\\$[^$\\s]+");
            });
        }

        @Test
        @DisplayName("같은 멱등 키로 다시 요청하면 같은 접수 번호, 콜백은 더 보내지 않음")
        void replays_same_key() {
            // given
            IssueResult first = service.issue(REQUEST);

            // when
            IssueResult second = service.issue(REQUEST);

            // then
            assertThat(second.supplierOrderNo()).isEqualTo(first.supplierOrderNo());
            assertThat(published).hasSize(1);
        }

        @Test
        @DisplayName("발급된 프로필은 RELEASED 상태로 조회된다")
        void profile_is_released_after_issue() {
            // given
            service.issue(REQUEST);
            String iccid = published.getFirst().event().iccid();

            // when
            ProfileView profile = service.findProfile(iccid).orElseThrow();

            // then
            assertThat(profile.state()).isEqualTo(ProfileState.RELEASED);
            assertThat(profile.activatedAt()).isNull();
            assertThat(profile.usedMb()).isZero();
        }
    }

    @Nested
    @DisplayName("장애 주입이 켜져 있으면")
    class Faults {

        @Test
        @DisplayName("난수가 발급 실패율보다 작으면 접수는 되지만 실패 콜백이 가고 프로필은 만들지 않는다")
        void issue_failure() {
            // given
            faults.update(new FaultSettings(0.5, 0, 0, false, false));
            nextRandom = 0.3;

            // when
            IssueResult result = service.issue(REQUEST);

            // then
            assertThat(result.supplierOrderNo()).isNotNull();
            assertThat(published).singleElement().satisfies(delivery -> {
                assertThat(delivery.event().eventType()).isEqualTo(CallbackEventType.ISSUE_FAILED);
                assertThat(delivery.event().iccid()).isNull();
                assertThat(delivery.event().reason()).isNotBlank();
            });
        }

        @Test
        @DisplayName("콜백 중복 2회면 같은 eventId의 콜백이 3건")
        void duplicate_callbacks() {
            // given
            faults.update(new FaultSettings(0, 0, 2, false, false));

            // when
            service.issue(REQUEST);

            // then
            assertThat(published).hasSize(3)
                    .extracting(d -> d.event().eventId()).containsOnly(published.getFirst().event().eventId());
        }

        @Test
        @DisplayName("콜백 누락이면 접수만 되고 콜백이 없다")
        void dropped_callback() {
            // given
            faults.update(new FaultSettings(0, 0, 0, true, false));

            // when
            service.issue(REQUEST);

            // then
            assertThat(published).isEmpty();
        }

        @Test
        @DisplayName("서명 오류면 서명이 틀린 콜백으로 표시된다")
        void invalid_signature() {
            // given
            faults.update(new FaultSettings(0, 0, 0, false, true));

            // when
            service.issue(REQUEST);

            // then
            assertThat(published).singleElement().extracting(CallbackDelivery::signatureValid).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("프로필 상태 변경(시연용)은")
    class ChangeState {

        @Test
        @DisplayName("ENABLED로 바꾸면 개통 시각이 기록된다")
        void enabling_records_activated_at() {
            // given
            service.issue(REQUEST);
            String iccid = published.getFirst().event().iccid();

            // when
            ProfileView enabled = service.changeState(iccid, ProfileState.ENABLED, NOW);

            // then
            assertThat(enabled.state()).isEqualTo(ProfileState.ENABLED);
            assertThat(enabled.activatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("모르는 ICCID면 404 PROFILE_NOT_FOUND")
        void unknown_iccid() {
            // when // then
            assertThatThrownBy(() -> service.changeState("8900000000000000000", ProfileState.INSTALLED, NOW))
                    .isInstanceOfSatisfying(MockSupplierException.class,
                            e -> assertThat(e.getCode()).isEqualTo("PROFILE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("유심 개통 요청은")
    class ActivateUsim {

        @Test
        @DisplayName("ICCID의 프로필을 ENABLED로 만들고 개통 시각을 돌려주며, 다시 요청해도 처음 개통 시각 그대로")
        void activates_once() {
            // when
            ProfileView first = service.activateUsim("8982300912345678902", NOW);
            ProfileView again = service.activateUsim("8982300912345678902", NOW.plusSeconds(60));

            // then
            assertThat(first.state()).isEqualTo(ProfileState.ENABLED);
            assertThat(first.activatedAt()).isEqualTo(NOW);
            assertThat(again.activatedAt()).isEqualTo(NOW);
            assertThat(service.findProfile("8982300912345678902")).get()
                    .extracting(ProfileView::state).isEqualTo(ProfileState.ENABLED);
        }
    }

    @Nested
    @DisplayName("개통 이벤트 콜백은 (과제 2.3-④)")
    class ActivationCallback {

        private String issuedIccid() {
            service.issue(REQUEST);
            String iccid = published.getFirst().event().iccid();
            published.clear();
            return iccid;
        }

        @Test
        @DisplayName("처음 ENABLED가 될 때 개통 시각을 담아 1번 발행하고, 다시 ENABLED로 바꿔도 더 보내지 않는다")
        void published_once_on_first_activation() {
            // given
            String iccid = issuedIccid();

            // when
            service.changeState(iccid, ProfileState.INSTALLED, NOW);
            service.changeState(iccid, ProfileState.ENABLED, NOW.plusSeconds(60));
            service.changeState(iccid, ProfileState.ENABLED, NOW.plusSeconds(120));

            // then
            assertThat(published).singleElement().satisfies(delivery -> {
                var event = delivery.event();
                assertThat(event.eventType()).isEqualTo(CallbackEventType.ACTIVATED);
                assertThat(event.eventId()).startsWith("sevt_");
                assertThat(event.iccid()).isEqualTo(iccid);
                assertThat(event.orderNo()).isEqualTo(REQUEST.orderNo());
                assertThat(event.activatedAt()).isEqualTo(NOW.plusSeconds(60));
            });
        }

        @Test
        @DisplayName("개통 콜백 누락을 켜면 개통 콜백만 빠지고 상태 조회에는 개통이 보인다")
        void activation_callback_drop() {
            // given
            String iccid = issuedIccid();
            faults.update(new FaultSettings(0, 0, 0, false, false, true));

            // when
            service.changeState(iccid, ProfileState.ENABLED, NOW);

            // then
            assertThat(published).isEmpty();
            assertThat(service.findProfile(iccid)).get().extracting(ProfileView::state).isEqualTo(ProfileState.ENABLED);
        }

        @Test
        @DisplayName("중복 설정이면 같은 eventId로 여러 번, 서명 오류 설정이면 틀린 서명으로 보낸다")
        void duplicates_and_invalid_signature() {
            // given
            String iccid = issuedIccid();
            faults.update(new FaultSettings(0, 0, 2, false, true));

            // when
            service.changeState(iccid, ProfileState.ENABLED, NOW);

            // then
            assertThat(published).hasSize(3);
            assertThat(published).extracting(d -> d.event().eventId()).containsOnly(published.getFirst().event().eventId());
            assertThat(published).allSatisfy(d -> assertThat(d.signatureValid()).isFalse());
        }

        @Test
        @DisplayName("유심 개통 요청도 개통 콜백을 보낸다 (공급사는 우리 주문번호를 모름)")
        void usim_activation() {
            // when
            service.activateUsim("8982300912345678902", NOW);

            // then
            assertThat(published).singleElement().satisfies(delivery -> {
                assertThat(delivery.event().eventType()).isEqualTo(CallbackEventType.ACTIVATED);
                assertThat(delivery.event().orderNo()).isNull();
                assertThat(delivery.event().iccid()).isEqualTo("8982300912345678902");
            });
        }
    }

    @Nested
    @DisplayName("유심 수령 카드(시연용)를 요청하면")
    class PickupCardIssue {

        @Test
        @DisplayName("체크 디지트가 맞는 19자리 ICCID를 주고, 같은 주문은 같은 번호, 다른 주문은 다른 번호")
        void issues_one_card_per_order() {
            // when
            PickupCard first = service.issuePickupCard("ORD-20261022-CARD0001");
            PickupCard again = service.issuePickupCard("ORD-20261022-CARD0001");
            PickupCard other = service.issuePickupCard("ORD-20261022-CARD0002");

            // then
            assertThat(first.iccid()).hasSize(19).containsOnlyDigits();
            assertThat(again.iccid()).isEqualTo(first.iccid());
            assertThat(other.iccid()).isNotEqualTo(first.iccid());
        }

        @Test
        @DisplayName("그 카드로 개통하면 프로필이 ENABLED가 되고, 개통 콜백에 카드를 받은 주문번호가 실린다")
        void activation_of_card_carries_order_no() {
            // given
            PickupCard card = service.issuePickupCard("ORD-20261022-CARD0003");

            // when
            ProfileView view = service.activateUsim(card.iccid(), NOW);

            // then
            assertThat(view.state()).isEqualTo(ProfileState.ENABLED);
            assertThat(published).singleElement()
                    .satisfies(delivery -> assertThat(delivery.event().orderNo()).isEqualTo("ORD-20261022-CARD0003"));
        }
    }
}
