package com.roampass.mock.pg.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹훅 본문 서명. HMAC-SHA256(공유 비밀키, 본문 원문 UTF-8) → 소문자 16진수
 */
public class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public WebhookSigner(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Webhook secret must not be empty");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
