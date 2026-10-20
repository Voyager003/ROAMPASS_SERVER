package com.roampass_server.issuance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.exception.IllegalStateTransitionException;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;

@DisplayName("SimProfile")
class SimProfileTest {

    private static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20260501-7K3Q9XPA");
    private static final Iccid ICCID = Iccid.of("8982300912345678902");
    private static final ActivationCode CODE = ActivationCode.of("LPA:1$smdp.example$X1Y2");
    private static final SupplierOrderNo SUPPLIER_ORDER_NO = SupplierOrderNo.of("SUP-000123");

    private static SimProfile esim() {
        return SimProfile.issueEsim(ORDER_NO, SUPPLIER_ORDER_NO, ICCID, CODE, 7, NOW);
    }

    @Nested
    @DisplayName("발급되면")
    class WhenIssued {

        @Test
        @DisplayName("이심은 ICCID·활성화 코드·공급사 접수 번호를 가진 ISSUED 상태다")
        void esim_starts_issued() {
            // when
            SimProfile profile = esim();

            // then
            assertThat(profile.status()).isEqualTo(InstallStatus.ISSUED);
            assertThat(profile.simType()).isEqualTo(SimType.ESIM);
            assertThat(profile.orderNo()).isEqualTo(ORDER_NO);
            assertThat(profile.iccid()).isEqualTo(ICCID);
            assertThat(profile.activationCode()).isEqualTo(CODE);
            assertThat(profile.supplierOrderNo()).isEqualTo(SUPPLIER_ORDER_NO);
            assertThat(profile.issuedAt()).isEqualTo(NOW);
            assertThat(profile.usagePeriod()).isNull(); // 아직 개통 전
        }

        @Test
        @DisplayName("유심은 ICCID 등록으로 만들어지고 활성화 코드가 없다")
        void usim_registered_with_iccid_only() {
            // when
            SimProfile profile = SimProfile.registerUsim(ORDER_NO, ICCID, 7, NOW);

            // then
            assertThat(profile.status()).isEqualTo(InstallStatus.ISSUED);
            assertThat(profile.simType()).isEqualTo(SimType.USIM);
            assertThat(profile.activationCode()).isNull();
            assertThat(profile.supplierOrderNo()).isNull();
        }

        @Test
        @DisplayName("이심에 활성화 코드가 없으면 거부된다")
        void esim_requires_activation_code() {
            // when & then
            assertThatThrownBy(() -> SimProfile.issueEsim(ORDER_NO, SUPPLIER_ORDER_NO, ICCID, null, 7, NOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_SIM_PROFILE");
        }
    }

    @Nested
    @DisplayName("설치 상태는 앞으로만 진행한다")
    class ForwardOnly {

        @Test
        @DisplayName("QR 열람 → 설치 → 개통 순서로 진행하며 각 시각이 기록된다")
        void progresses_in_order() {
            // given
            Instant viewed = NOW.plusSeconds(10);
            Instant installed = NOW.plusSeconds(60);
            Instant activated = NOW.plusSeconds(3_600);

            // when
            SimProfile profile = esim().markQrViewed(viewed).markInstalled(installed).markActivated(activated);

            // then
            assertThat(profile.status()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(profile.qrViewedAt()).isEqualTo(viewed);
            assertThat(profile.installedAt()).isEqualTo(installed);
            assertThat(profile.activatedAt()).isEqualTo(activated);
        }

        @Test
        @DisplayName("설치 신호가 빠져도 바로 개통(ACTIVE)이 될 수 있고, 개통은 설치를 포함한 것으로 본다")
        void can_skip_to_active() {
            // given
            Instant activated = NOW.plusSeconds(3_600);

            // when
            SimProfile profile = esim().markActivated(activated);

            // then
            assertThat(profile.status()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(profile.installedAt()).isEqualTo(activated);
        }

        @Test
        @DisplayName("개통 뒤에 늦게 도착한 설치 신호는 아무것도 바꾸지 않는다")
        void late_install_signal_after_active_is_ignored() {
            // given
            SimProfile active = esim().markActivated(NOW.plusSeconds(3_600));

            // when
            SimProfile result = active.markInstalled(NOW.plusSeconds(60));

            // then
            assertThat(result).isSameAs(active);
        }

        @Test
        @DisplayName("같은 신호가 두 번 오면 처음 시각을 유지한다")
        void duplicate_signal_keeps_first_time() {
            // given
            SimProfile installed = esim().markInstalled(NOW.plusSeconds(60));

            // when
            SimProfile again = installed.markInstalled(NOW.plusSeconds(120));

            // then
            assertThat(again).isSameAs(installed);
            assertThat(again.installedAt()).isEqualTo(NOW.plusSeconds(60));
        }

        @Test
        @DisplayName("설치 뒤의 QR 열람 기록은 상태를 되돌리지 않는다")
        void qr_view_after_install_does_not_go_back() {
            // given
            SimProfile installed = esim().markInstalled(NOW.plusSeconds(60));

            // when
            SimProfile result = installed.markQrViewed(NOW.plusSeconds(90));

            // then
            assertThat(result.status()).isEqualTo(InstallStatus.INSTALLED);
        }

        @Test
        @DisplayName("유심에는 QR이 없어서 QR 열람을 기록할 수 없다")
        void usim_has_no_qr() {
            // given
            SimProfile usim = SimProfile.registerUsim(ORDER_NO, ICCID, 7, NOW);

            // when & then
            assertThatThrownBy(() -> usim.markQrViewed(NOW))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("개통되면")
    class WhenActivated {

        // 부록 A 예시 시각
        private final Instant activatedAt = Instant.parse("2026-05-01T15:20:00Z");

        @Test
        @DisplayName("개통 시각 + 사용 일수 × 24시간으로 만료 시각이 정해진다")
        void usage_period_starts_at_activation() {
            // when
            SimProfile profile = esim().markActivated(activatedAt);

            // then
            assertThat(profile.usagePeriod()).isEqualTo(UsagePeriod.of(activatedAt, 7));
            assertThat(profile.usagePeriod().expiresAt()).isEqualTo(Instant.parse("2026-05-08T15:20:00Z"));
        }

        @Test
        @DisplayName("만료 시각이 되면 스케줄러가 처리하기 전이라도 조회 상태는 EXPIRED다")
        void effectively_expired_at_deadline() {
            // given
            SimProfile active = esim().markActivated(activatedAt);
            Instant deadline = active.usagePeriod().expiresAt();

            // when & then
            assertThat(active.effectiveStatus(deadline.minusSeconds(1))).isEqualTo(InstallStatus.ACTIVE);
            assertThat(active.effectiveStatus(deadline)).isEqualTo(InstallStatus.EXPIRED);
        }

        @Test
        @DisplayName("만료 시각이 지나면 EXPIRED로 바뀐다")
        void mark_expired_after_deadline() {
            // given
            SimProfile active = esim().markActivated(activatedAt);

            // when
            SimProfile expired = active.markExpired(active.usagePeriod().expiresAt());

            // then
            assertThat(expired.status()).isEqualTo(InstallStatus.EXPIRED);
        }

        @Test
        @DisplayName("만료 시각 전이거나 개통 전이면 만료시킬 수 없다")
        void cannot_expire_early_or_before_activation() {
            // given
            SimProfile active = esim().markActivated(activatedAt);

            // when & then
            assertThatThrownBy(() -> active.markExpired(active.usagePeriod().expiresAt().minusSeconds(1)))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThatThrownBy(() -> esim().markExpired(NOW.plusSeconds(999_999)))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("환불 가능 여부는")
    class Refundability {

        @Test
        @DisplayName("발급만 됐거나 QR만 열어 본 SIM은 환불할 수 있다")
        void refundable_before_install() {
            // when & then
            assertThat(esim().isRefundable()).isTrue();
            assertThat(esim().markQrViewed(NOW.plusSeconds(10)).isRefundable()).isTrue();
        }

        @Test
        @DisplayName("설치되거나 개통된 SIM은 환불할 수 없다")
        void not_refundable_once_installed_or_active() {
            // when & then
            assertThat(esim().markInstalled(NOW.plusSeconds(60)).isRefundable()).isFalse();
            assertThat(esim().markActivated(NOW.plusSeconds(60)).isRefundable()).isFalse();
        }

        @Test
        @DisplayName("설치했다가 지운 SIM도 환불할 수 없다")
        void not_refundable_after_delete() {
            // given
            SimProfile installed = esim().markInstalled(NOW.plusSeconds(60));

            // when
            SimProfile deleted = installed.markDeleted(NOW.plusSeconds(120));

            // then
            assertThat(deleted.isRefundable()).isFalse();
        }
    }

    @Nested
    @DisplayName("기기에서 프로필을 지우면 (공급사 DELETED)")
    class WhenDeleted {

        @Test
        @DisplayName("설치 상태는 도달한 단계를 유지하고 삭제 시각만 기록된다")
        void keeps_status_and_records_deleted_at() {
            // given
            SimProfile active = esim().markActivated(NOW.plusSeconds(3_600));

            // when
            SimProfile deleted = active.markDeleted(NOW.plusSeconds(7_200));

            // then
            assertThat(deleted.status()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(deleted.deletedAt()).isEqualTo(NOW.plusSeconds(7_200));
            assertThat(deleted.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("삭제는 설치를 전제로 하므로 설치 신호 없이 삭제가 오면 설치된 것으로 본다")
        void delete_implies_installed() {
            // when
            SimProfile deleted = esim().markDeleted(NOW.plusSeconds(120));

            // then
            assertThat(deleted.installedAt()).isEqualTo(NOW.plusSeconds(120));
            assertThat(deleted.isRefundable()).isFalse();
        }

        @Test
        @DisplayName("삭제 신호가 다시 오면 처음 시각을 유지한다")
        void duplicate_delete_keeps_first_time() {
            // given
            SimProfile deleted = esim().markInstalled(NOW).markDeleted(NOW.plusSeconds(120));

            // when
            SimProfile again = deleted.markDeleted(NOW.plusSeconds(999));

            // then
            assertThat(again).isSameAs(deleted);
        }
    }

    @Test
    @DisplayName("주문번호가 같으면 같은 프로필이다 (주문당 프로필 1개)")
    void equality_is_by_order_no() {
        // when & then
        assertThat(esim().markActivated(NOW.plusSeconds(60))).isEqualTo(esim());
    }

    @Nested
    @DisplayName("저장소에서 복원하면")
    class WhenRestored {

        @Test
        @DisplayName("모든 값을 그대로 가진다")
        void keeps_all_values() {
            // when
            SimProfile profile = SimProfile.restore(3L, ORDER_NO, SimType.ESIM,
                    ICCID, CODE, SUPPLIER_ORDER_NO, 7, InstallStatus.ACTIVE, NOW, null, NOW.plusSeconds(60),
                    NOW.plusSeconds(120), null);

            // then
            assertThat(profile.id()).isEqualTo(3L);
            assertThat(profile.status()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(profile.usageDays()).isEqualTo(7);
            assertThat(profile.activatedAt()).isEqualTo(NOW.plusSeconds(120));
        }
    }
}
