package com.roampass_server.issuance.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.issuance.application.IssuanceAcceptanceService;
import com.roampass_server.issuance.application.IssuanceCallbackProcessor;
import com.roampass_server.issuance.application.IssuanceCallbackService;
import com.roampass_server.issuance.application.IssuanceDispatchService;
import com.roampass_server.issuance.application.IssuanceFailureService;
import com.roampass_server.issuance.application.IssuanceStartService;
import com.roampass_server.issuance.application.IssuanceTimeoutService;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierClient;
import com.roampass_server.issuance.infrastructure.external.HttpSupplierClient;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;
import com.roampass_server.plan.application.PlanQueryService;

/**
 * 발급 유스케이스·공급사 어댑터 빈 등록
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IssuanceProperties.class)
public class IssuanceConfig {

    @Bean
    SupplierClient supplierClient(IssuanceProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new HttpSupplierClient(RestClient.builder()
                .baseUrl(properties.supplierBaseUrl())
                .requestFactory(requestFactory)
                .build());
    }

    @Bean
    IssuanceDispatchService issuanceDispatchService(OrderRepository orderRepository,
                                                    IssuanceRequestRepository requestRepository,
                                                    IssuanceStartService startService,
                                                    IssuanceAcceptanceService acceptanceService,
                                                    IssuanceFailureService failureService,
                                                    SupplierClient supplierClient, IssuanceProperties properties) {
        return new IssuanceDispatchService(orderRepository, requestRepository, startService, acceptanceService,
                failureService, supplierClient, properties.batchSize());
    }

    @Bean
    IssuanceStartService issuanceStartService(OrderRepository orderRepository,
                                              IssuanceRequestRepository requestRepository,
                                              StatusHistoryRecorder historyRecorder, Clock clock) {
        return new IssuanceStartService(orderRepository, requestRepository, historyRecorder, clock);
    }

    @Bean
    IssuanceAcceptanceService issuanceAcceptanceService(IssuanceRequestRepository requestRepository) {
        return new IssuanceAcceptanceService(requestRepository);
    }

    @Bean
    IssuanceFailureService issuanceFailureService(IssuanceRequestRepository requestRepository,
                                                  OrderRepository orderRepository, RefundOpener refundOpener,
                                                  StatusHistoryRecorder historyRecorder, Clock clock) {
        return new IssuanceFailureService(requestRepository, orderRepository, refundOpener, historyRecorder, clock);
    }

    @Bean
    IssuanceTimeoutService issuanceTimeoutService(IssuanceRequestRepository requestRepository,
                                                  IssuanceFailureService failureService, Clock clock,
                                                  IssuanceProperties properties) {
        return new IssuanceTimeoutService(requestRepository, failureService, clock, properties.callbackTimeout(),
                properties.batchSize());
    }

    @Bean
    IssuanceCallbackProcessor issuanceCallbackProcessor(CallbackLogRepository callbackLogRepository,
                                                        IssuanceRequestRepository requestRepository,
                                                        SimProfileRepository profileRepository,
                                                        OrderRepository orderRepository,
                                                        PlanQueryService planQueryService,
                                                        IssuanceFailureService failureService,
                                                        StatusHistoryRecorder historyRecorder, Clock clock) {
        return new IssuanceCallbackProcessor(callbackLogRepository, requestRepository, profileRepository,
                orderRepository, planQueryService, failureService, historyRecorder, clock);
    }

    @Bean
    IssuanceCallbackService issuanceCallbackService(IssuanceProperties properties,
                                                    CallbackLogRepository callbackLogRepository,
                                                    IssuanceCallbackProcessor processor, Clock clock) {
        return new IssuanceCallbackService(new HmacSignature(properties.callbackSecret()), callbackLogRepository,
                processor, clock);
    }
}
