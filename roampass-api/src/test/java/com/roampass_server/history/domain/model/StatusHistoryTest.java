package com.roampass_server.history.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.Actor;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;

@DisplayName("StatusHistory")
class StatusHistoryTest {

    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final OrderNo ORDER_NO = OrderNo.of("ORD-20261020-7K3Q9XPA");
    private static final HistoryTarget ORDER = HistoryTarget.order(ORDER_NO);

    // 어떤 enum이든 상태로 사용 가능: 이력은 주문·결제·SIM 상태 흐름의 상태 이름을 함께 저장
    private enum SampleStatus { PENDING, PAID, NEEDS_ATTENTION, REFUNDING }

    @Nested
    @DisplayName("상태 변경을 기록하면")
    class WhenRecorded {

        @Test
        @DisplayName("주문번호·대상·이전/이후 상태·주체·사유·시각을 가진다")
        void keeps_everything() {
            // when
            StatusHistory history = StatusHistory.record(ORDER_NO, ORDER, SampleStatus.PENDING, SampleStatus.PAID,
                    Actor.pg(), "승인 웹훅 수신", NOW);

            // then
            assertThat(history.orderNo()).isEqualTo(ORDER_NO);
            assertThat(history.target()).isEqualTo(ORDER);
            assertThat(history.fromStatus()).isEqualTo("PENDING");
            assertThat(history.toStatus()).isEqualTo("PAID");
            assertThat(history.actor()).isEqualTo(Actor.pg());
            assertThat(history.reason()).isEqualTo("승인 웹훅 수신");
            assertThat(history.occurredAt()).isEqualTo(NOW);
            assertThat(history.id()).isNull();
        }

        @Test
        @DisplayName("생성 기록은 이전 상태가 없다")
        void creation_has_no_from_status() {
            // when
            StatusHistory history = StatusHistory.record(ORDER_NO, ORDER, null, SampleStatus.PENDING,
                    Actor.user(UserId.of("user-1")), "주문 생성", NOW);

            // then
            assertThat(history.fromStatus()).isNull();
            assertThat(history.toStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("사유가 500자를 넘으면 잘린다")
        void truncates_long_reason() {
            // when
            StatusHistory history = StatusHistory.record(ORDER_NO, ORDER, SampleStatus.PENDING, SampleStatus.PAID,
                    Actor.system(), "r".repeat(1_000), NOW);

            // then
            assertThat(history.reason()).hasSize(500);
        }
    }

    @Nested
    @DisplayName("운영자 조치는")
    class AdminAction {

        @Test
        @DisplayName("사유와 함께 기록된다")
        void recorded_with_reason() {
            // when
            StatusHistory history = StatusHistory.record(ORDER_NO, ORDER, SampleStatus.NEEDS_ATTENTION,
                    SampleStatus.REFUNDING, Actor.admin(AdminId.of("admin-kim")), "고객 문의로 수동 환불", NOW);

            // then
            assertThat(history.actor().asString()).isEqualTo("ADMIN:admin-kim");
            assertThat(history.reason()).isEqualTo("고객 문의로 수동 환불");
        }

        @Test
        @DisplayName("사유가 없으면 ADMIN_REASON_REQUIRED로 거부된다")
        void requires_reason() {
            // when & then
            assertThatThrownBy(() -> StatusHistory.record(ORDER_NO, ORDER, SampleStatus.NEEDS_ATTENTION,
                    SampleStatus.REFUNDING, Actor.admin(AdminId.of("admin-kim")), " ", NOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("ADMIN_REASON_REQUIRED");
        }
    }

    @Nested
    @DisplayName("잘못된 기록은 거부된다")
    class Invalid {

        @Test
        @DisplayName("이전 상태와 이후 상태가 같으면 상태 변경이 아니다")
        void rejects_same_status() {
            // when & then
            assertThatThrownBy(() -> StatusHistory.record(ORDER_NO, ORDER, SampleStatus.PAID, SampleStatus.PAID,
                    Actor.system(), "x", NOW))
                    .isInstanceOf(InvalidValueException.class)
                    .extracting("code").isEqualTo("INVALID_HISTORY");
        }

        @Test
        @DisplayName("주문번호·대상·이후 상태·주체·시각 중 하나라도 없으면")
        void rejects_missing_fields() {
            // when & then
            assertThatThrownBy(() -> StatusHistory.record(null, ORDER, null, SampleStatus.PENDING, Actor.system(), "x", NOW))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> StatusHistory.record(ORDER_NO, null, null, SampleStatus.PENDING, Actor.system(), "x", NOW))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> StatusHistory.record(ORDER_NO, ORDER, null, null, Actor.system(), "x", NOW))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> StatusHistory.record(ORDER_NO, ORDER, null, SampleStatus.PENDING, null, "x", NOW))
                    .isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> StatusHistory.record(ORDER_NO, ORDER, null, SampleStatus.PENDING, Actor.system(), "x", null))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
