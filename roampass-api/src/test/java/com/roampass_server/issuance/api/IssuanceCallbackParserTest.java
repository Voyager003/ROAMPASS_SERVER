package com.roampass_server.issuance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.issuance.application.IssuanceCallbackCommand;

import tools.jackson.databind.json.JsonMapper;

@DisplayName("공급사 콜백 본문 해석")
class IssuanceCallbackParserTest {

    private final IssuanceCallbackParser parser = new IssuanceCallbackParser(JsonMapper.builder().build());

    @Test
    @DisplayName("mock-supplier 콜백 본문을 명령으로 바꾼다 (모르는 필드는 무시)")
    void parses() {
        // when
        IssuanceCallbackCommand command = parser.parse("""
                {"eventId":"sevt_1","eventType":"ISSUED","supplierOrderNo":"SUP-1","orderNo":"ORD-20261021-AAAA1111",
                 "iccid":"8982300912345678902","activationCode":"LPA:1$smdp.example$X1Y2","reason":null,
                 "occurredAt":"2026-10-21T00:00:03Z"}
                """);

        // then
        assertThat(command).isEqualTo(new IssuanceCallbackCommand("sevt_1", "ISSUED", "SUP-1",
                "ORD-20261021-AAAA1111", "8982300912345678902", "LPA:1$smdp.example$X1Y2", null));
    }

    @Test
    @DisplayName("개통 이벤트 콜백 본문(유심이면 주문번호 없음)의 개통 시각을 담는다")
    void parses_activation() {
        // when
        IssuanceCallbackCommand command = parser.parse("""
                {"eventId":"sevt_2","eventType":"ACTIVATED","supplierOrderNo":"SUP-2","orderNo":null,
                 "iccid":"8982300912345678902","activationCode":null,"reason":null,
                 "activatedAt":"2026-05-01T15:20:00Z","occurredAt":"2026-05-01T15:20:03Z"}
                """);

        // then
        assertThat(command).isEqualTo(new IssuanceCallbackCommand("sevt_2", "ACTIVATED", "SUP-2", null,
                "8982300912345678902", null, null, "2026-05-01T15:20:00Z"));
    }

    @Test
    @DisplayName("JSON이 아니면 400 INVALID_CALLBACK")
    void rejects_malformed() {
        // when // then
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOfSatisfying(InvalidValueException.class,
                        e -> assertThat(e.getCode()).isEqualTo("INVALID_CALLBACK"));
    }
}
