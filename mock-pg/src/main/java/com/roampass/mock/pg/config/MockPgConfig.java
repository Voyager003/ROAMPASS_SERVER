package com.roampass.mock.pg.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass.mock.pg.fault.FaultConfig;
import com.roampass.mock.pg.fault.FaultProperties;
import com.roampass.mock.pg.webhook.HttpWebhookPublisher;
import com.roampass.mock.pg.webhook.WebhookProperties;
import com.roampass.mock.pg.webhook.WebhookPublisher;

import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WebhookProperties.class, FaultProperties.class})
public class MockPgConfig {

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService webhookScheduler() {
        return Executors.newScheduledThreadPool(2);
    }

    @Bean
    FaultConfig faultConfig(FaultProperties properties) {
        return new FaultConfig(properties.toSettings(), () -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    WebhookPublisher webhookPublisher(WebhookProperties properties, JsonMapper jsonMapper,
                                      ScheduledExecutorService webhookScheduler) {
        return new HttpWebhookPublisher(properties, jsonMapper, webhookScheduler);
    }
}
