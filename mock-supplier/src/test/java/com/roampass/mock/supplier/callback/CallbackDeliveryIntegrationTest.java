package com.roampass.mock.supplier.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;

/**
 * 발급 요청 후 콜백이 실제 HTTP로 도착하는지, X-Signature가 본문과 맞는지, 서명 오류 주입 시 틀린 서명이 가는지 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("콜백 발송")
class CallbackDeliveryIntegrationTest {

    private static final String SECRET = "test-callback-secret";
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer RECEIVER = startReceiver();

    record Received(String signature, String body) {
    }

    private static HttpServer startReceiver() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/callback", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.add(new Received(exchange.getRequestHeaders().getFirst("X-Signature"), body));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void callbackProperties(DynamicPropertyRegistry registry) {
        registry.add("mock-supplier.callback.url",
                () -> "http://127.0.0.1:" + RECEIVER.getAddress().getPort() + "/callback");
        registry.add("mock-supplier.callback.secret", () -> SECRET);
        registry.add("mock-supplier.callback.delay", () -> "0s");
    }

    @AfterAll
    static void stopReceiver() {
        RECEIVER.stop(0);
    }

    @Autowired
    private MockMvcTester mvc;

    @AfterEach
    void reset() {
        RECEIVED.clear();
        assertThat(mvc.post().uri("/mock/supplier/config/reset")).hasStatusOk();
    }

    private void issue(String key) {
        assertThat(mvc.post().uri("/mock/supplier/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idempotencyKey":"%s","orderNo":"ORD-20261020-AAAA1111"}
                        """.formatted(key))).hasStatus2xxSuccessful();
    }

    @Test
    @DisplayName("발급 완료 콜백이 1건 도착하고 서명은 수신한 본문의 HMAC과 같다")
    void delivers_signed_callback() {
        // when
        issue("K-cb-1");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> !RECEIVED.isEmpty());
        Received received = RECEIVED.getFirst();
        assertThat(received.signature()).isEqualTo(new CallbackSigner(SECRET).sign(received.body()));
        assertThat((String) JsonPath.read(received.body(), "$.eventType")).isEqualTo("ISSUED");
        assertThat((String) JsonPath.read(received.body(), "$.iccid")).hasSize(19);
        assertThat(RECEIVED).hasSize(1);
    }

    @Test
    @DisplayName("서명 오류를 켜면 본문의 HMAC과 다른 서명이 간다")
    void invalid_signature_fault() {
        // given
        assertThat(mvc.put().uri("/mock/supplier/config").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invalidSignature":true}
                        """)).hasStatusOk();

        // when
        issue("K-cb-2");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> !RECEIVED.isEmpty());
        Received received = RECEIVED.getFirst();
        assertThat(received.signature()).isNotEqualTo(new CallbackSigner(SECRET).sign(received.body()));
    }
}
