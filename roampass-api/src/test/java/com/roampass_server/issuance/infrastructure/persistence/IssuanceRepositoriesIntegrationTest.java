package com.roampass_server.issuance.infrastructure.persistence;

import static com.roampass_server.support.PersistenceTestFixtures.NOW;
import static com.roampass_server.support.PersistenceTestFixtures.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.ActivationCode;
import com.roampass_server.issuance.domain.model.Iccid;
import com.roampass_server.issuance.domain.model.InstallStatus;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.model.SimProfile;
import com.roampass_server.issuance.domain.model.SupplierOrderNo;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.support.IntegrationTest;

@IntegrationTest
@Transactional
@DisplayName("발급 요청·SIM 프로필 저장소 (PostgreSQL)")
class IssuanceRepositoriesIntegrationTest {

    private static final ActivationCode CODE = ActivationCode.of("LPA:1$smdp.example$X1Y2");

    @Autowired
    private IssuanceRequestRepository requestRepository;

    @Autowired
    private SimProfileRepository profileRepository;

    @Autowired
    private OrderRepository orderRepository;

    private OrderNo orderNo;

    @BeforeEach
    void saveOrder() {
        orderNo = orderRepository.save(pendingOrder("ISSU0001", NOW)).orderNo();
    }

    @Nested
    @DisplayName("발급 요청은")
    class Requests {

        @Test
        @DisplayName("저장·최신 조회·개수, 조건부 저장은 한 번만 반영")
        void save_find_and_conditional_update() {
            // given
            IssuanceRequest saved = requestRepository.save(IssuanceRequest.start(orderNo, 1, NOW));
            IssuanceRequest accepted = saved.accept(SupplierOrderNo.of("SUP-DB-1"));

            // when
            boolean first = requestRepository.saveIfStatus(accepted, IssuanceStatus.PENDING);
            boolean second = requestRepository.saveIfStatus(accepted, IssuanceStatus.PENDING);

            // then
            assertThat(first).isTrue();
            assertThat(second).isFalse();
            assertThat(requestRepository.countByOrderNo(orderNo)).isEqualTo(1);
            assertThat(requestRepository.findLatestByOrderNo(orderNo)).get().satisfies(r -> {
                assertThat(r.status()).isEqualTo(IssuanceStatus.ACCEPTED);
                assertThat(r.supplierOrderNo()).isEqualTo(SupplierOrderNo.of("SUP-DB-1"));
                assertThat(r.requestedAt()).isEqualTo(NOW);
            });
        }

        @Test
        @DisplayName("PENDING 조회, 기준 시각 이전에 요청된 열린 요청 조회")
        void finds_pending_and_open() {
            // given
            OrderNo other = orderRepository.save(pendingOrder("ISSU0002", NOW)).orderNo();
            requestRepository.save(IssuanceRequest.start(orderNo, 1, NOW));
            IssuanceRequest done = requestRepository.save(IssuanceRequest.start(other, 1, NOW));
            requestRepository.saveIfStatus(done.complete(SupplierOrderNo.of("SUP-DB-2"), NOW), IssuanceStatus.PENDING);

            // when // then
            assertThat(requestRepository.findPending(10)).extracting(IssuanceRequest::orderNo).contains(orderNo)
                    .doesNotContain(other);
            assertThat(requestRepository.findOpenRequestedBefore(NOW.plusSeconds(1), 10))
                    .extracting(IssuanceRequest::orderNo).contains(orderNo).doesNotContain(other);
            assertThat(requestRepository.findOpenRequestedBefore(NOW.minusSeconds(1), 10))
                    .extracting(IssuanceRequest::orderNo).doesNotContain(orderNo);
        }
    }

    @Nested
    @DisplayName("SIM 프로필은")
    class Profiles {

        @Test
        @DisplayName("저장 후 모든 값이 그대로 돌아온다")
        void round_trips() {
            // given
            profileRepository.save(SimProfile.issueEsim(orderNo, SupplierOrderNo.of("SUP-P-1"),
                    Iccid.of("8982300912345678902"), CODE, 5, NOW));

            // when
            SimProfile found = profileRepository.findByOrderNo(orderNo).orElseThrow();

            // then
            assertThat(found.id()).isNotNull();
            assertThat(found.iccid().value()).isEqualTo("8982300912345678902");
            assertThat(found.activationCode()).isEqualTo(CODE);
            assertThat(found.usageDays()).isEqualTo(5);
            assertThat(found.issuedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("한 주문에 두 번째 프로필은 DB 제약으로 거부된다 (중복 콜백의 최후 방어)")
        void one_profile_per_order() {
            // given
            profileRepository.save(SimProfile.issueEsim(orderNo, SupplierOrderNo.of("SUP-P-2"),
                    Iccid.of("8982300912345678902"), CODE, 5, NOW));

            // when // then
            assertThatThrownBy(() -> profileRepository.save(SimProfile.issueEsim(orderNo,
                    SupplierOrderNo.of("SUP-P-3"), Iccid.of("8982300912345678910"), CODE, 5, NOW)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("폴링 대상 조회는")
    class Trackable {

        @Test
        @DisplayName("여행 시작일(10/25) 3일 전 ~ 시작일 + 사용 일수(5일) 사이의 미개통 프로필만, 개통되면 빠진다")
        void window_and_status() {
            // given
            profileRepository.save(SimProfile.issueEsim(orderNo, SupplierOrderNo.of("SUP-T-1"),
                    Iccid.of("8982300912345678902"), CODE, 5, NOW));

            // when // then
            assertThat(trackable(LocalDate.of(2026, 10, 21))).doesNotContain(orderNo);
            assertThat(trackable(LocalDate.of(2026, 10, 22))).contains(orderNo);
            assertThat(trackable(LocalDate.of(2026, 10, 30))).contains(orderNo);
            assertThat(trackable(LocalDate.of(2026, 10, 31))).doesNotContain(orderNo);

            SimProfile found = profileRepository.findByOrderNo(orderNo).orElseThrow();
            assertThat(profileRepository.saveIfStatus(found.markActivated(NOW), InstallStatus.ISSUED)).isTrue();
            assertThat(trackable(LocalDate.of(2026, 10, 22))).doesNotContain(orderNo);
            assertThat(profileRepository.findByOrderNo(orderNo).orElseThrow().activatedAt()).isEqualTo(NOW);
        }

        private List<OrderNo> trackable(LocalDate today) {
            return profileRepository.findTrackable(today, 3, 100).stream().map(SimProfile::orderNo).toList();
        }
    }
}
