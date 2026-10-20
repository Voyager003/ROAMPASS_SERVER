package com.roampass_server.issuance.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.issuance.application.IssuanceAcceptanceService;
import com.roampass_server.issuance.application.IssuanceCallbackProcessor;
import com.roampass_server.issuance.application.IssuanceCallbackService;
import com.roampass_server.issuance.application.IssuanceFailureService;
import com.roampass_server.issuance.application.IssuanceStartService;
import com.roampass_server.issuance.application.IssuanceTimeoutService;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.RefundOpener;
import com.roampass_server.plan.application.PlanQueryService;

/**
 * 발급 유스케이스 빈 등록. 공급사 호출이 필요한 발급 요청 조율은 공급사 어댑터와 함께 등록
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IssuanceProperties.class)
public class IssuanceConfig {

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
