package com.roampass_server.callback.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 통지 본문 서명 검증. HMAC-SHA256(공유 비밀키, 본문 원문 UTF-8) → 소문자 16진수
 * 비교는 MessageDigest.isEqual(길이만 같으면 일정 시간 비교)
 */
public class HmacSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacSignature(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("Signature secret must not be empty");
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

    public boolean verify(String body, String signature) {
        if (body == null || signature == null) {
            return false;
        }
        return MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
