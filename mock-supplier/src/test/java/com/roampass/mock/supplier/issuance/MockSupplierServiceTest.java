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
}
