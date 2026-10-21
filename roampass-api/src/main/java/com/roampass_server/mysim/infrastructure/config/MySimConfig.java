package com.roampass_server.mysim.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.issuance.application.ProfileSignalApplier;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.issuance.infrastructure.config.IssuanceProperties;
import com.roampass_server.mysim.application.MyRefundExecutor;
import com.roampass_server.mysim.application.MyRefundService;
import com.roampass_server.mysim.application.MySimQueryService;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;

@Configuration(proxyBeanMethods = false)
public class MySimConfig {

    @Bean
    MySimQueryService mySimQueryService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                                        IssuanceRequestRepository issuanceRequestRepository, Clock clock,
                                        IssuanceProperties issuanceProperties) {
        return new MySimQueryService(orderRepository, profileRepository, issuanceRequestRepository, clock,
                issuanceProperties.callbackTimeout());
    }

    @Bean
    MyRefundExecutor myRefundExecutor(OrderRepository orderRepository, RefundOpener refundOpener,
                                      StatusHistoryRecorder historyRecorder, Clock clock) {
        return new MyRefundExecutor(orderRepository, refundOpener, historyRecorder, clock);
    }

    @Bean
    MyRefundService myRefundService(MyRefundExecutor executor, OrderRepository orderRepository,
                                    SimProfileRepository profileRepository, SupplierClient supplierClient,
                                    ProfileSignalApplier applier) {
        return new MyRefundService(executor, orderRepository, profileRepository, supplierClient, applier);
    }
}
