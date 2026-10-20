package com.roampass_server.plan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.plan.application.PlanQueryService;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.model.PlanSpec;
import com.roampass_server.plan.domain.repository.PlanSearchCondition;

@WebMvcTest(controllers = PlanController.class)
@DisplayName("GET /api/plans")
class PlanControllerTest {

    private static final Plan JP_ESIM = Plan.of(1L, PlanSpec.of("JP", 5, 3_072), SimType.ESIM, Money.krw(9_900));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PlanQueryService planQueryService;

    @Nested
    @DisplayName("요금제 목록을 조회하면")
    class List_ {

        @Test
        @DisplayName("헤더 없이 호출되고 요금제 필드와 가격을 돌려준다")
        void returns_plans_without_auth_header() {
            // given
            given(planQueryService.search(any())).willReturn(List.of(JP_ESIM));

            // when
            var result = mvc.get().uri("/api/plans");

            // then
            assertThat(result).hasStatusOk().bodyJson()
                    .isLenientlyEqualTo("""
                            [{"planId":1,"countryCode":"JP","days":5,"dataMb":3072,"simType":"ESIM",
                              "price":{"amount":9900,"currency":"KRW"}}]
                            """);
        }

        @Test
        @DisplayName("쿼리 파라미터가 검색 조건으로 그대로 전달된다")
        void passes_query_parameters_as_condition() {
            // given
            given(planQueryService.search(any())).willReturn(List.of());

            // when
            var result = mvc.get().uri("/api/plans?country=JP&simType=ESIM&days=5");

            // then
            assertThat(result).hasStatusOk();
            verify(planQueryService).search(PlanSearchCondition.of("JP", SimType.ESIM, 5, null));
        }
    }

    @Nested
    @DisplayName("잘못된 조건이면")
    class InvalidFilter {

        @Test
        @DisplayName("국가 코드 형식이 틀리면 400 INVALID_PLAN_FILTER")
        void malformed_country() {
            // when
            var result = mvc.get().uri("/api/plans?country=jp");

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_PLAN_FILTER");
        }

        @Test
        @DisplayName("없는 SIM 종류면 400 INVALID_REQUEST")
        void unknown_sim_type() {
            // when
            var result = mvc.get().uri("/api/plans?simType=PHYSICAL");

            // then
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        }
    }
}
