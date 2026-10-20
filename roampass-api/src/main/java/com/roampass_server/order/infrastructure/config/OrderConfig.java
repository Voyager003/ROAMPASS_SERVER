package com.roampass_server.order.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.order.application.OrderExpirationService;
import com.roampass_server.order.application.OrderService;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.order.domain.service.OrderNoGenerator;
import com.roampass_server.order.domain.service.RandomOrderNoGenerator;
import com.roampass_server.plan.application.PlanQueryService;

/**
 * 주문 유스케이스 빈 등록. 설정값(결제 대기 시간, 묶음 크기)을 생성자로 주입하기 위해 @Bean으로 등록
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderProperties.class)
public class OrderConfig {

    @Bean
    OrderNoGenerator orderNoGenerator() {
        return new RandomOrderNoGenerator();
    }

    @Bean
    OrderService orderService(OrderRepository orderRepository, OrderNoGenerator orderNoGenerator,
                              PlanQueryService planQueryService, StatusHistoryRecorder historyRecorder,
                              Clock clock, OrderProperties properties) {
        return new OrderService(orderRepository, orderNoGenerator, planQueryService, historyRecorder, clock,
                properties.paymentWindow());
    }

    @Bean
    OrderExpirationService orderExpirationService(OrderRepository orderRepository,
                                                  StatusHistoryRecorder historyRecorder, Clock clock,
                                                  OrderProperties properties) {
        return new OrderExpirationService(orderRepository, historyRecorder, clock,
                properties.expiration().batchSize());
    }
}
