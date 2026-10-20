package com.roampass.mock.supplier.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.roampass.mock.supplier.callback.CallbackProperties;
import com.roampass.mock.supplier.callback.CallbackPublisher;
import com.roampass.mock.supplier.callback.HttpCallbackPublisher;
import com.roampass.mock.supplier.fault.FaultConfig;
import com.roampass.mock.supplier.fault.FaultProperties;
import com.roampass.mock.supplier.issuance.IccidGenerator;

import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CallbackProperties.class, FaultProperties.class})
public class MockSupplierConfig {

    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService callbackScheduler() {
        return Executors.newScheduledThreadPool(2);
    }

    @Bean
    FaultConfig faultConfig(FaultProperties properties) {
        return new FaultConfig(properties.toSettings(), () -> ThreadLocalRandom.current().nextDouble());
    }

    @Bean
    IccidGenerator iccidGenerator() {
        return new IccidGenerator();
    }

    @Bean
    CallbackPublisher callbackPublisher(CallbackProperties properties, JsonMapper jsonMapper,
                                        ScheduledExecutorService callbackScheduler, FaultConfig faultConfig) {
        return new HttpCallbackPublisher(properties, jsonMapper, callbackScheduler,
                () -> faultConfig.current().callbackDelayMs());
    }
}
