package com.roampass.mock.supplier.callback;

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
import org.springframework.web.client.RestClientResponseException;

import com.roampass.mock.supplier.LogMask;

import tools.jackson.databind.json.JsonMapper;

/**
 * 콜백을 지연(설정 + 장애 주입) 후 비동기 HTTP POST로 발송. 헤더 X-Signature = HMAC-SHA256(본문 원문)
 * 서명 오류 주입이면 다른 비밀키로 서명. 발송 실패는 로그만, 재시도 없음
 */
public class HttpCallbackPublisher implements CallbackPublisher {

    static final String SIGNATURE_HEADER = "X-Signature";

    private static final Logger log = LoggerFactory.getLogger(HttpCallbackPublisher.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final CallbackProperties properties;
    private final CallbackSigner signer;
    private final CallbackSigner wrongSigner;
    private final JsonMapper jsonMapper;
    private final ScheduledExecutorService scheduler;
    private final java.util.function.LongSupplier extraDelayMillis;
    private final RestClient restClient;

    public HttpCallbackPublisher(CallbackProperties properties, JsonMapper jsonMapper,
                                 ScheduledExecutorService scheduler, java.util.function.LongSupplier extraDelayMillis) {
        this.properties = properties;
        this.signer = new CallbackSigner(properties.secret());
        this.wrongSigner = new CallbackSigner(properties.secret() + "-wrong");
        this.jsonMapper = jsonMapper;
        this.scheduler = scheduler;
        this.extraDelayMillis = extraDelayMillis;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public void publish(CallbackDelivery delivery) {
        if (!properties.enabled()) {
            return;
        }
        long base = properties.delay() == null ? 0 : properties.delay().toMillis();
        scheduler.schedule(() -> send(delivery), base + extraDelayMillis.getAsLong(), TimeUnit.MILLISECONDS);
    }

    private void send(CallbackDelivery delivery) {
        String body = jsonMapper.writeValueAsString(delivery.event());
        CallbackSigner chosen = delivery.signatureValid() ? signer : wrongSigner;
        try {
            restClient.post().uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(SIGNATURE_HEADER, chosen.sign(body))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("event=supplier_callback_sent eventId={} type={} orderNo={} iccid={} signatureValid={}",
                    delivery.event().eventId(), delivery.event().eventType(), orderNoOf(delivery),
                    LogMask.iccid(delivery.event().iccid()), delivery.signatureValid());
        } catch (RestClientResponseException e) {
            log.warn("event=supplier_callback_failed eventId={} type={} orderNo={} signatureValid={} status={}",
                    delivery.event().eventId(), delivery.event().eventType(), orderNoOf(delivery),
                    delivery.signatureValid(), e.getStatusCode().value());
        } catch (RestClientException e) {
            log.warn("event=supplier_callback_failed eventId={} type={} orderNo={} error=\"{}\"",
                    delivery.event().eventId(), delivery.event().eventType(), orderNoOf(delivery), e.getMessage());
        }
    }

    private static String orderNoOf(CallbackDelivery delivery) {
        return delivery.event().orderNo() == null ? "-" : delivery.event().orderNo();
    }
}
