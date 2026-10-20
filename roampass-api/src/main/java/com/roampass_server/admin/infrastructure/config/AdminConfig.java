package com.roampass_server.admin.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.admin.application.AdminOrderQueryService;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.repository.RefundAttemptRepository;

@Configuration(proxyBeanMethods = false)
public class AdminConfig {

    @Bean
    AdminOrderQueryService adminOrderQueryService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                                  SimProfileRepository profileRepository,
                                                  IssuanceRequestRepository issuanceRequestRepository,
                                                  RefundAttemptRepository refundAttemptRepository,
                                                  StatusHistoryRepository historyRepository,
                                                  CallbackLogRepository callbackLogRepository) {
        return new AdminOrderQueryService(orderRepository, paymentRepository, profileRepository,
                issuanceRequestRepository, refundAttemptRepository, historyRepository, callbackLogRepository);
    }
}
