package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;

@DisplayName("QR 열람 기록")
class QrViewServiceTest {

    private static final UserId USER = UserId.of("user-1");

    private final IssuanceFixture f = new IssuanceFixture();

    private InstallStatus status() {
        return f.profiles.findByOrderNo(ORDER_NO).orElseThrow().status();
    }

    @Test
    @DisplayName("발급된 이심이면 QR_VIEWED, 이력 주체 USER, 환불 가능 판정은 그대로(설치가 아님)")
    void marks_viewed() {
        // given
        f.issued();

        // when
        f.qrView.markViewed(USER, ORDER_NO);

        // then
        assertThat(status()).isEqualTo(InstallStatus.QR_VIEWED);
        assertThat(f.profiles.findByOrderNo(ORDER_NO).orElseThrow().isRefundable()).isTrue();
        assertThat(f.histories.findByOrderNo(ORDER_NO))
                .filteredOn(h -> "QR_VIEWED".equals(h.toStatus()))
                .extracting(StatusHistory::actor).containsExactly(Actor.user(USER));
    }

    @Test
    @DisplayName("이미 설치된 뒤면 변화 없음(늦게 온 신호는 무시)")
    void ignores_after_install() {
        // given
        f.issued();
        f.clock.advance(Duration.ofDays(1));
        f.supplier.profileReturns(Iccid.of(IssuanceFixture.ICCID),
                SupplierProfileResult.found(SupplierProfileState.INSTALLED, null, 0));
        f.tracking.poll();

        // when
        f.qrView.markViewed(USER, ORDER_NO);

        // then
        assertThat(status()).isEqualTo(InstallStatus.INSTALLED);
    }

    @Test
    @DisplayName("남의 주문은 404 ORDER_NOT_FOUND, 아직 발급 전이면 404 SIM_NOT_FOUND")
    void not_found() {
        // given
        f.dispatched();

        // when // then
        assertThatThrownBy(() -> f.qrView.markViewed(UserId.of("user-2"), ORDER_NO))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_FOUND"));
        assertThatThrownBy(() -> f.qrView.markViewed(USER, ORDER_NO))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("SIM_NOT_FOUND"));
    }
}
