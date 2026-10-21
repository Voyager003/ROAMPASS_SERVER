package com.roampass_server.admin.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.roampass_server.admin.application.AdminActionExecutor;
import com.roampass_server.admin.application.AdminActionService;
import com.roampass_server.admin.application.AdminIssueQueryService;
import com.roampass_server.admin.application.AdminOrderQueryService;
import com.roampass_server.admin.application.AdminOrderSearch;
import com.roampass_server.admin.infrastructure.persistence.JdbcAdminOrderSearch;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.application.ProfileSignalApplier;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AdminConfig.AdminProperties.class)
public class AdminConfig {

    /**
     * 어드민 설정({@code roampass.admin.*})
     *
     * @param stuckThreshold 결제 승인 후 이 시간이 지나도록 발급되지 않으면 이상 건
     * @param issueLimit     이상 건 묶음별 최대 건수
     */
    @ConfigurationProperties(prefix = "roampass.admin")
    public record AdminProperties(Duration stuckThreshold, int issueLimit) {
    }

    @Bean
    AdminOrderSearch adminOrderSearch(DataSource dataSource) {
        return new JdbcAdminOrderSearch(new NamedParameterJdbcTemplate(dataSource));
    }

    @Bean
    AdminOrderQueryService adminOrderQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                                  SimProfileRepository profileRepository,
                                                  IssuanceRequestRepository issuanceRequestRepository,
                                                  RefundAttemptRepository refundAttemptRepository,
                                                  StatusHistoryRepository historyRepository,
                                                  CallbackLogRepository callbackLogRepository,
                                                  AdminOrderSearch orderSearch, Clock clock) {
        return new AdminOrderQueryService(orderRepository, paymentRepository, profileRepository,
                issuanceRequestRepository, refundAttemptRepository, historyRepository, callbackLogRepository,
                orderSearch, clock);
    }

    @Bean
    AdminIssueQueryService adminIssueQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                                  CallbackLogRepository callbackLogRepository,
                                                  AdminOrderQueryService orderQueryService, Clock clock,
                                                  AdminProperties properties) {
        return new AdminIssueQueryService(orderRepository, paymentRepository, callbackLogRepository,
                orderQueryService, clock, properties.stuckThreshold(), properties.issueLimit());
    }

    @Bean
    AdminActionExecutor adminActionExecutor(OrderRepository orderRepository,
                                            IssuanceRequestRepository issuanceRequestRepository,
                                            RefundOpener refundOpener, StatusHistoryRecorder historyRecorder,
                                            Clock clock) {
        return new AdminActionExecutor(orderRepository, issuanceRequestRepository, refundOpener, historyRecorder,
                clock);
    }

    @Bean
    AdminActionService adminActionService(AdminActionExecutor executor, SimProfileRepository profileRepository,
                                          SupplierClient supplierClient, ProfileSignalApplier applier) {
        return new AdminActionService(executor, profileRepository, supplierClient, applier);
    }
}
