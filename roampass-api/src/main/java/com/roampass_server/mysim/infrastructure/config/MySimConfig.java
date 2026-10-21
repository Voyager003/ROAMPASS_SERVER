package com.roampass_server.mysim.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.infrastructure.config.IssuanceProperties;
import com.roampass_server.mysim.application.MySimQueryService;
import com.roampass_server.order.domain.repository.OrderRepository;

@Configuration(proxyBeanMethods = false)
public class MySimConfig {

    @Bean
    MySimQueryService mySimQueryService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                                        IssuanceRequestRepository issuanceRequestRepository, Clock clock,
                                        IssuanceProperties issuanceProperties) {
        return new MySimQueryService(orderRepository, profileRepository, issuanceRequestRepository, clock,
                issuanceProperties.callbackTimeout());
    }
}
