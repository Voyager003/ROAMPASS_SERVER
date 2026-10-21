package com.roampass_server.issuance.application;

import static com.roampass_server.issuance.application.IssuanceFixture.ORDER_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.application.exception.ConflictException;
import com.roampass_server.common.application.exception.NotFoundException;
import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.model.TargetType;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.service.SupplierProfileResult;
import com.roampass_server.issuance.domain.service.SupplierProfileState;
import com.roampass_server.order.domain.model.OrderStatus;

@DisplayName("유심 ICCID 등록·개통")
class UsimRegistrationServiceTest {

    private static final UserId USER = UserId.of("user-1");
    private static final String ICCID = "8982300912345678902";
    private static final Instant ACTIVATED_AT = Instant.parse("2026-10-21T00:00:05Z");

    private final IssuanceFixture f = new IssuanceFixture();

    private SimProfile profile() {
        return f.profiles.findByOrderNo(ORDER_NO).orElseThrow();
    }

    @Test
    @DisplayName("수령 대기 주문에 올바른 ICCID를 등록하면 주문 ISSUED, 유심 프로필 생성, 공급사 개통 응답으로 ACTIVE")
    void registers_and_activates() {
        // given
        f.pickupReadyUsimOrder(ORDER_NO);
        f.supplier.activationReturns(Iccid.of(ICCID),
                SupplierProfileResult.found(SupplierProfileState.ENABLED, ACTIVATED_AT, 0));

        // when
        UsimRegistrationResult result = f.usim.register(USER, ORDER_NO, ICCID);

        // then
        assertThat(result.installStatus()).isEqualTo(InstallStatus.ACTIVE);
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.ISSUED);
        assertThat(profile().simType()).isEqualTo(SimType.USIM);
        assertThat(profile().activatedAt()).isEqualTo(ACTIVATED_AT);
        assertThat(profile().usageDays()).isEqualTo(5);
        assertThat(f.histories.findByOrderNo(ORDER_NO))
                .extracting(h -> h.target().type(), StatusHistory::toStatus, StatusHistory::actor)
                .containsExactly(
                        tuple(TargetType.ORDER, "ISSUED", Actor.user(USER)),
                        tuple(TargetType.SIM_PROFILE, "ISSUED", Actor.user(USER)),
                        tuple(TargetType.SIM_PROFILE, "ACTIVE", Actor.supplier()));
    }

    @Test
    @DisplayName("개통 응답을 못 받으면 ISSUED로 남고, 폴링이 공급사 ENABLED를 읽어 ACTIVE로 확정")
    void polling_fills_lost_activation() {
        // given
        f.pickupReadyUsimOrder(ORDER_NO);

        // when
        UsimRegistrationResult result = f.usim.register(USER, ORDER_NO, ICCID);
        f.clock.advance(Duration.ofDays(1)); // 여행 시작일 창 안
        f.supplier.profileReturns(Iccid.of(ICCID),
                SupplierProfileResult.found(SupplierProfileState.ENABLED, ACTIVATED_AT, 0));
        f.tracking.poll();

        // then
        assertThat(result.installStatus()).isEqualTo(InstallStatus.ISSUED);
        assertThat(profile().status()).isEqualTo(InstallStatus.ACTIVE);
    }

    @Test
    @DisplayName("체크 디지트가 틀린 ICCID는 400 INVALID_ICCID, 주문·프로필 변화 없음")
    void rejects_invalid_iccid() {
        // given
        f.pickupReadyUsimOrder(ORDER_NO);

        // when // then
        assertThatThrownBy(() -> f.usim.register(USER, ORDER_NO, "8982300912345678903"))
                .isInstanceOfSatisfying(InvalidValueException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_ICCID"));
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.PICKUP_READY);
        assertThat(f.profiles.count()).isZero();
    }

    @Test
    @DisplayName("다른 주문에 이미 등록된 ICCID는 409 ICCID_ALREADY_REGISTERED")
    void rejects_duplicate_iccid() {
        // given
        f.pickupReadyUsimOrder(ORDER_NO);
        OrderNo other = OrderNo.of("ORD-20261021-BBBB2222");
        f.pickupReadyUsimOrder(other);
        f.usim.register(USER, other, ICCID);

        // when // then
        assertThatThrownBy(() -> f.usim.register(USER, ORDER_NO, ICCID))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ICCID_ALREADY_REGISTERED"));
        assertThat(f.orderStatus()).isEqualTo(OrderStatus.PICKUP_READY);
    }

    @Test
    @DisplayName("이미 등록한 주문·이심 주문은 409 ORDER_NOT_REGISTRABLE, 남의 주문은 404")
    void rejects_not_registrable() {
        // given
        f.pickupReadyUsimOrder(ORDER_NO);
        f.usim.register(USER, ORDER_NO, ICCID);

        // when // then
        assertThatThrownBy(() -> f.usim.register(USER, ORDER_NO, "8982300912345678910"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ORDER_NOT_REGISTRABLE"));
        assertThatThrownBy(() -> f.usim.register(UserId.of("user-2"), ORDER_NO, ICCID))
                .isInstanceOf(NotFoundException.class);
    }
}
