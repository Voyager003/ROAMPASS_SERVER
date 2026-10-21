package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ICCID;
import static com.roampass_server.issuance.application.IssuanceFixture.NOW;
import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;

/**
 * 개통 이벤트 콜백(과제 2.3-④ "상태 조회 API와 개통 이벤트 콜백을 조합").
 * 콜백이 먼저 반영하고, 빠지거나 늦은 것은 폴링이 메움. 두 경로 모두 같은 반영 코드(ProfileSignalApplier)
 */
@DisplayName("공급사 개통 이벤트 콜백")
class ActivationCallbackTest {

    private static final Instant ACTIVATED_AT = Instant.parse("2026-10-25T15:20:00Z");

    private final IssuanceFixture f = new IssuanceFixture();

    private SimProfile profile() {
        return f.profiles.findByOrderNo(ORDER_NO).orElseThrow();
    }

    private List<StatusHistory> profileHistory() {
        return f.histories.findByOrderNo(ORDER_NO).stream()
                .filter(h -> h.target().type() == TargetType.SIM_PROFILE)
                .toList();
    }

    @Test
    @DisplayName("발급된 이심에 개통 콜백이 오면 ACTIVE, 개통 시각은 공급사 값, 이력 사유는 '공급사 개통 콜백'")
    void activates_issued_esim() {
        // given
        f.issued();

        // when
        CallbackResult result = f.sendCallback(f.activated("sevt_act", ACTIVATED_AT));

        // then
        assertThat(result).isEqualTo(CallbackResult.PROCESSED);
        assertThat(profile().status()).isEqualTo(InstallStatus.ACTIVE);
        assertThat(profile().activatedAt()).isEqualTo(ACTIVATED_AT);
        assertThat(profile().usagePeriod().expiresAt()).isEqualTo(ACTIVATED_AT.plus(Duration.ofDays(5)));
        assertThat(profileHistory()).last().satisfies(h -> {
            assertThat(h.toStatus()).isEqualTo("ACTIVE");
            assertThat(h.actor().asString()).isEqualTo("SUPPLIER");
            assertThat(h.reason()).isEqualTo("공급사 개통 콜백: ENABLED");
        });
        assertThat(f.callbackLogs.findByEventKey("SUPPLIER:sevt_act")).get().satisfies(log -> {
            assertThat(log.outcome()).isEqualTo(CallbackOutcome.PROCESSED);
            assertThat(log.orderNo()).isEqualTo(ORDER_NO);
        });
    }

    @Test
    @DisplayName("같은 eventId가 다시 오면 DUPLICATE, 개통 이력은 1건")
    void duplicate_event() {
        // given
        f.issued();
        f.sendCallback(f.activated("sevt_act", ACTIVATED_AT));

        // when
        CallbackResult again = f.sendCallback(f.activated("sevt_act", ACTIVATED_AT));

        // then
        assertThat(again).isEqualTo(CallbackResult.DUPLICATE);
        assertThat(profileHistory()).filteredOn(h -> h.toStatus().equals("ACTIVE")).hasSize(1);
    }

    @Test
    @DisplayName("폴링이 먼저 개통을 반영했으면 늦게 온 콜백은 ALREADY_APPLIED, 이력 추가 없음")
    void polling_applied_first() {
        // given
        f.issued();
        f.applier.apply(profile(), SupplierProfileResult.found(SupplierProfileState.ENABLED, ACTIVATED_AT, 0));
        int before = profileHistory().size();

        // when
        CallbackResult result = f.sendCallback(f.activated("sevt_late", ACTIVATED_AT));

        // then
        assertThat(result).isEqualTo(CallbackResult.ALREADY_APPLIED);
        assertThat(profileHistory()).hasSize(before);
        assertThat(f.callbackLogs.findByEventKey("SUPPLIER:sevt_late")).get()
                .extracting(CallbackLog::outcome).isEqualTo(CallbackOutcome.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("폴링이 설치(INSTALLED)만 반영한 상태에서 개통 콜백이 오면 ACTIVE로 진행")
    void after_installed_by_polling() {
        // given
        f.issued();
        f.applier.apply(profile(), SupplierProfileResult.found(SupplierProfileState.INSTALLED, null, 0));

        // when
        CallbackResult result = f.sendCallback(f.activated("sevt_act", ACTIVATED_AT));

        // then
        assertThat(result).isEqualTo(CallbackResult.PROCESSED);
        assertThat(profileHistory()).extracting(StatusHistory::reason)
                .containsSubsequence("공급사 상태 조회: INSTALLED", "공급사 개통 콜백: ENABLED");
    }

    @Test
    @DisplayName("유심은 주문번호 없이 ICCID만으로 와도 등록된 프로필을 찾아 개통")
    void usim_without_order_no() {
        // given
        OrderNo usim = OrderNo.of("ORD-20261021-USIM0001");
        f.pickupReadyUsimOrder(usim);
        f.supplier.activationReturns(Iccid.of(ICCID), SupplierProfileResult.unknown()); // 개통 응답 유실
        f.usim.register(UserId.of("user-1"), usim, ICCID);

        // when
        CallbackResult result = f.sendCallback(f.activated("sevt_usim", ACTIVATED_AT));

        // then
        assertThat(result).isEqualTo(CallbackResult.PROCESSED);
        assertThat(f.profiles.findByOrderNo(usim).orElseThrow().status()).isEqualTo(InstallStatus.ACTIVE);
    }

    @Test
    @DisplayName("우리에게 없는 ICCID면 ORDER_NOT_FOUND로 기록만 하고 아무것도 바꾸지 않음")
    void unknown_iccid() {
        // when
        CallbackResult result = f.sendCallback(f.activated("sevt_unknown", ACTIVATED_AT));

        // then
        assertThat(result).isEqualTo(CallbackResult.ORDER_NOT_FOUND);
        assertThat(f.callbackLogs.findByEventKey("SUPPLIER:sevt_unknown")).get().satisfies(log -> {
            assertThat(log.outcome()).isEqualTo(CallbackOutcome.TARGET_NOT_FOUND);
            assertThat(log.orderNo()).isNull();
        });
    }

    @Test
    @DisplayName("개통 시각 형식이 틀리면 INVALID_CALLBACK(400)")
    void malformed_activated_at() {
        // given
        f.issued();

        // when // then
        assertThatThrownBy(() -> f.sendCallback(new IssuanceCallbackCommand("sevt_bad", "ACTIVATED", null, null, ICCID,
                null, null, "2026-10-25 15:20")))
                .isInstanceOf(InvalidValueException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_CALLBACK");
        assertThat(profile().status()).isEqualTo(InstallStatus.ISSUED);
    }
}
