package com.roampass_server.payment.infrastructure.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass_server.callback.domain.repository.CallbackLogRepository;
import com.roampass_server.callback.domain.service.HmacSignature;
import com.roampass_server.history.application.StatusHistoryRecorder;
import com.roampass_server.order.domain.repository.OrderRepository;
import com.roampass_server.payment.application.PaymentConfirmationService;
import com.roampass_server.payment.application.PaymentRequestService;
import com.roampass_server.payment.application.PaymentWebhookProcessor;
import com.roampass_server.payment.application.PaymentWebhookService;
import com.roampass_server.payment.domain.repository.PaymentRepository;

/**
 * 결제 유스케이스 빈 등록. PG 호출이 필요한 결제 요청 조율(PaymentService)은 PG 어댑터와 함께 등록
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
