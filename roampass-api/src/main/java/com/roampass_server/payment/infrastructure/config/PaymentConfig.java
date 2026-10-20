package com.roampass_server.payment.infrastructure.config;

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
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.application.PaymentService;
import com.roampass_server.payment.application.PaymentWebhookProcessor;
import com.roampass_server.payment.application.PaymentWebhookService;
import com.roampass_server.payment.domain.repository.PaymentRepository;
import com.roampass_server.payment.domain.service.PgClient;
import com.roampass_server.payment.infrastructure.external.HttpPgClient;

/**
 * 결제 유스케이스·PG 어댑터 빈 등록
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig {

    @Bean
    PaymentRequestService paymentRequestService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                                                StatusHistoryRecorder historyRecorder, Clock clock) {
        return new PaymentRequestService(orderRepository, paymentRepository, historyRecorder, clock);
    }

    @Bean
    PaymentConfirmationService paymentConfirmationService(PaymentRepository paymentRepository,
                                                          OrderRepository orderRepository,
                                                          StatusHistoryRecorder historyRecorder, Clock clock) {
        return new PaymentConfirmationService(paymentRepository, orderRepository, historyRecorder, clock);
    }

    @Bean
    PgClient pgClient(PaymentProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new HttpPgClient(RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build());
    }

    @Bean
    PaymentService paymentService(PaymentRequestService requestService,
                                  PaymentConfirmationService confirmationService, PgClient pgClient,
                                  PaymentRepository paymentRepository, OrderRepository orderRepository, Clock clock) {
        return new PaymentService(requestService, confirmationService, pgClient, paymentRepository, orderRepository,
                clock);
    }

    @Bean
    PaymentWebhookProcessor paymentWebhookProcessor(CallbackLogRepository callbackLogRepository,
                                                    PaymentConfirmationService confirmationService, Clock clock) {
        return new PaymentWebhookProcessor(callbackLogRepository, confirmationService, clock);
    }

    @Bean
    PaymentWebhookService paymentWebhookService(PaymentProperties properties,
                                                CallbackLogRepository callbackLogRepository,
                                                PaymentWebhookProcessor processor, Clock clock) {
        return new PaymentWebhookService(new HmacSignature(properties.webhookSecret()), callbackLogRepository,
                processor, clock);
    }
}
