package com.roampass.mock.pg.webhook;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.json.JsonMapper;

/**
 * 웹훅을 지연 후 비동기 HTTP POST로 발송. 승인 응답보다 늦게 도착하는 실제 PG 동작 흉내
 * 서명 대상은 직렬화한 본문 문자열 그대로(수신 측도 받은 원문으로 검증)
 * 발송 실패는 로그만, 재시도 없음
 */
public class HttpWebhookPublisher implements WebhookPublisher {

    static final String SIGNATURE_HEADER = "X-PG-Signature";
    static final String EVENT_ID_HEADER = "X-PG-Event-Id";

    private static final Logger log = LoggerFactory.getLogger(HttpWebhookPublisher.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final WebhookProperties properties;
    private final WebhookSigner signer;
    private final JsonMapper jsonMapper;
    private final ScheduledExecutorService scheduler;
    private final RestClient restClient;

    public HttpWebhookPublisher(WebhookProperties properties, JsonMapper jsonMapper,
                                ScheduledExecutorService scheduler) {
        this.properties = properties;
        this.signer = new WebhookSigner(properties.secret());
        this.jsonMapper = jsonMapper;
        this.scheduler = scheduler;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void publish(WebhookEvent event) {
        if (!properties.enabled()) {
            return;
        }
        long delayMillis = properties.delay() == null ? 0 : properties.delay().toMillis();
        scheduler.schedule(() -> send(event), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void send(WebhookEvent event) {
        String body = jsonMapper.writeValueAsString(event);
        try {
            restClient.post().uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SIGNATURE_HEADER, signer.sign(body))
                    .header(EVENT_ID_HEADER, event.eventId())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Webhook sent: {} {}", event.eventType(), event.eventId());
        } catch (RestClientException e) {
            log.warn("Webhook delivery failed: {} {}", event.eventId(), e.getMessage());
        }
    }
}
