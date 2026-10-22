package com.roampass_server.issuance.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.roampass_server.issuance.application.DemoSimService;
import com.roampass_server.issuance.application.ProfileSignalApplier;
import com.roampass_server.issuance.domain.repository.SimProfileRepository;
import com.roampass_server.issuance.domain.service.SupplierDemoClient;
import com.roampass_server.issuance.infrastructure.external.HttpSupplierDemoClient;
import com.roampass_server.order.domain.repository.OrderRepository;

/**
 * 시연 기능 빈. roampass.demo.enabled=true 일 때만 등록(기본 꺼짐, 실제 공급사엔 없는 기능)
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "roampass.demo", name = "enabled", havingValue = "true")
public class DemoConfig {

    @Bean
    SupplierDemoClient supplierDemoClient(IssuanceProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new HttpSupplierDemoClient(RestClient.builder()
                .baseUrl(properties.supplierBaseUrl())
                .requestFactory(requestFactory)
                .build());
    }

    @Bean
    DemoSimService demoSimService(OrderRepository orderRepository, SimProfileRepository profileRepository,
                                  SupplierDemoClient supplierDemoClient, ProfileSignalApplier applier, Clock clock) {
        return new DemoSimService(orderRepository, profileRepository, supplierDemoClient, applier, clock);
    }
}
