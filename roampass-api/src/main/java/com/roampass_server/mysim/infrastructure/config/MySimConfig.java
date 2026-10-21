package com.roampass_server.mysim.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.mysim.application.MySimQueryService;
import com.roampass_server.order.domain.repository.OrderRepository;

@Configuration(proxyBeanMethods = false)
public class MySimConfig {

    @Bean
    MySimQueryService mySimQueryService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                                        Clock clock) {
        return new MySimQueryService(orderRepository, profileRepository, clock);
    }
}
