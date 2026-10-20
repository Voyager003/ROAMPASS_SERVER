package com.roampass.mock.pg.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
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
 * 승인 후 웹훅이 실제 HTTP로 도착하는지, 서명이 본문과 맞는지 검증
 * 수신 측은 JDK 내장 HttpServer(임의 포트)
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("웹훅 발송")
class WebhookDeliveryIntegrationTest {

    private static final String SECRET = "test-webhook-secret";
    private static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    private static final HttpServer RECEIVER = startReceiver();

    record Received(String signature, String eventId, String body) {
    }

    private static HttpServer startReceiver() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/webhook", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.add(new Received(exchange.getRequestHeaders().getFirst("X-PG-Signature"),
                        exchange.getRequestHeaders().getFirst("X-PG-Event-Id"), body));
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
    static void webhookProperties(DynamicPropertyRegistry registry) {
        registry.add("mock-pg.webhook.url",
                () -> "http://127.0.0.1:" + RECEIVER.getAddress().getPort() + "/webhook");
        registry.add("mock-pg.webhook.secret", () -> SECRET);
        registry.add("mock-pg.webhook.delay", () -> "0s");
    }

    @AfterAll
    static void stopReceiver() {
        RECEIVER.stop(0);
    }

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("승인되면 서명된 승인 웹훅이 1건 도착하고, 서명은 수신한 본문의 HMAC과 같다")
    void delivers_signed_webhook() throws Exception {
        // when
        var result = mvc.post().uri("/mock/pg/approve").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idempotencyKey":"K-hook-1","orderNo":"ORD-20261020-AAAA1111","amount":9900,"currency":"KRW"}
                        """)
                .exchange();
        String pgTid = JsonPath.read(result.getResponse().getContentAsString(), "$.pgTid");

        // then
        await().atMost(Duration.ofSeconds(5)).until(() -> !RECEIVED.isEmpty());
        Received received = RECEIVED.getFirst();
        assertThat(received.signature()).isEqualTo(new WebhookSigner(SECRET).sign(received.body()));
        assertThat((String) JsonPath.read(received.body(), "$.eventId")).isEqualTo(received.eventId());
        assertThat((String) JsonPath.read(received.body(), "$.eventType")).isEqualTo("PAYMENT_APPROVED");
        assertThat((String) JsonPath.read(received.body(), "$.pgTid")).isEqualTo(pgTid);
        assertThat(RECEIVED).hasSize(1);
    }
}
