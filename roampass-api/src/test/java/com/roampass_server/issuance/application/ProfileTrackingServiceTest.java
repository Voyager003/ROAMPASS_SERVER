package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ICCID;
import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;

@DisplayName("설치·개통 추적 폴링")
class ProfileTrackingServiceTest {

    private static final Iccid ID = Iccid.of(ICCID);

    private final IssuanceFixture f = new IssuanceFixture();

    private SimProfile profile() {
        return f.profiles.findByOrderNo(ORDER_NO).orElseThrow();
    }

    private void supplierSays(SupplierProfileState state, Instant activatedAt) {
        f.supplier.profileReturns(ID, SupplierProfileResult.found(state, activatedAt, 0));
    }

    @Nested
    @DisplayName("폴링 대상은")
    class Targets {

        @Test
        @DisplayName("여행 시작일(10/25) 3일 전보다 이르면 조회하지 않는다 (오늘 10/21)")
        void skips_far_future_trip() {
            // given
            f.issued();

            // when
            f.tracking.poll();

            // then
            assertThat(f.supplier.profileQueries()).isEmpty();
        }

        @Test
        @DisplayName("시작일 3일 전(10/22)부터 조회하고, 사용 기간이 끝난 뒤(10/31)에는 조회하지 않는다")
        void polls_within_window_only() {
            // given
            f.issued();

            // when
            f.clock.advance(Duration.ofDays(1));   // 10/22
            f.tracking.poll();
            f.clock.advance(Duration.ofDays(9));   // 10/31 (시작일 + 5일 = 10/30 이후)
            f.tracking.poll();

            // then
            assertThat(f.supplier.profileQueries()).containsExactly(ID);
        }

        @Test
        @DisplayName("개통(ACTIVE)되면 더 이상 조회하지 않는다")
        void stops_after_activation() {
            // given
            f.issued();
            f.clock.advance(Duration.ofDays(1));
            supplierSays(SupplierProfileState.ENABLED, f.clock.instant());
            f.tracking.poll();

            // when
            f.tracking.poll();

            // then
            assertThat(f.supplier.profileQueries()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("공급사 상태가")
    class Signals {

        @Test
        @DisplayName("ENABLED면 ACTIVE, 개통 시각은 공급사 값, 설치 시각도 채움, 이력 주체 SUPPLIER")
        void enabled() {
            // given
            f.issued();
            f.clock.advance(Duration.ofDays(1));
            Instant activatedAt = Instant.parse("2026-10-21T22:30:00Z");
            supplierSays(SupplierProfileState.ENABLED, activatedAt);

            // when
            int changed = f.tracking.poll();

            // then
            assertThat(changed).isEqualTo(1);
            assertThat(profile().status()).isEqualTo(InstallStatus.ACTIVE);
            assertThat(profile().activatedAt()).isEqualTo(activatedAt);
            assertThat(profile().installedAt()).isNotNull();
            assertThat(f.histories.findByOrderNo(ORDER_NO))
                    .filteredOn(h -> h.target().type() == TargetType.SIM_PROFILE && "ACTIVE".equals(h.toStatus()))
                    .extracting(StatusHistory::fromStatus, StatusHistory::actor)
                    .containsExactly(tuple("ISSUED", Actor.supplier()));
        }

        @Test
        @DisplayName("INSTALLED면 INSTALLED, 다음 조회에서 ENABLED면 ACTIVE")
        void installed_then_enabled() {
            // given
            f.issued();
            f.clock.advance(Duration.ofDays(1));
            supplierSays(SupplierProfileState.INSTALLED, null);
            f.tracking.poll();
            assertThat(profile().status()).isEqualTo(InstallStatus.INSTALLED);

            // when
            supplierSays(SupplierProfileState.ENABLED, f.clock.instant());
            f.tracking.poll();

            // then
            assertThat(profile().status()).isEqualTo(InstallStatus.ACTIVE);
        }

        @Test
        @DisplayName("DELETED면 상태는 그대로 두고 삭제 시각만 기록, 이후 조회하지 않음")
        void deleted() {
            // given
            f.issued();
            f.clock.advance(Duration.ofDays(1));
            supplierSays(SupplierProfileState.DELETED, null);

            // when
            f.tracking.poll();
            f.tracking.poll();

            // then
            assertThat(profile().status()).isEqualTo(InstallStatus.ISSUED);
            assertThat(profile().deletedAt()).isNotNull();
            assertThat(f.supplier.profileQueries()).hasSize(1);
        }

        @Test
        @DisplayName("RELEASED·조회 실패면 변화 없음, 다음 주기에 다시 조회")
        void unchanged() {
            // given
            f.issued();
            f.clock.advance(Duration.ofDays(1));
            supplierSays(SupplierProfileState.RELEASED, null);
            f.tracking.poll();
            f.supplier.profileReturns(ID, SupplierProfileResult.unknown());

            // when
            int changed = f.tracking.poll();

            // then
            assertThat(changed).isZero();
            assertThat(profile().status()).isEqualTo(InstallStatus.ISSUED);
            assertThat(f.supplier.profileQueries()).hasSize(2);
        }
    }
}
