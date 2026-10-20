package com.roampass_server.order.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.roampass_server.common.domain.OrderNo;

@DisplayName("RandomOrderNoGenerator")
class RandomOrderNoGeneratorTest {

    private final RandomOrderNoGenerator generator = new RandomOrderNoGenerator();

    @Test
    @DisplayName("날짜 부분은 UTC가 아니라 KST 기준이다")
    void date_part_uses_kst() {
        // given — 2026-10-20 15:30 UTC is already 2026-10-21 00:30 in KST
        Instant now = Instant.parse("2026-10-20T15:30:00Z");

        // when
        OrderNo orderNo = generator.next(now);

        // then
        assertThat(orderNo.value()).startsWith("ORD-20261021-");
    }

    @Test
    @DisplayName("연속으로 만들어도 서로 다른 번호가 나온다")
    void generates_distinct_numbers() {
        // given
        Instant now = Instant.parse("2026-10-20T03:00:00Z");
        Set<String> generated = new HashSet<>();

        // when
        for (int i = 0; i < 1_000; i++) {
            generated.add(generator.next(now).value());
        }

        // then
        assertThat(generated).hasSize(1_000);
    }
}
