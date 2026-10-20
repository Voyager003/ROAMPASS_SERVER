package com.roampass_server.order.domain.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.roampass_server.common.domain.KoreaTime;
import com.roampass_server.common.domain.OrderNo;

/**
 * {@code ORD-{KST yyyyMMdd}-{랜덤 8자}}. 문의 전화에서 소리 내어 읽을 때 헷갈리지 않도록
 * 0/O와 1/I는 문자 집합에서 제외. 하루에 32^8 ≈ 1.1 × 10^12가지 조합,
 * 충돌 발생 시 저장 시점에 order_no 유니크 제약이 검출
 */
public class RandomOrderNoGenerator implements OrderNoGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int RANDOM_LENGTH = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final SecureRandom random = new SecureRandom();

    @Override
    public OrderNo next(Instant now) {
        StringBuilder suffix = new StringBuilder(RANDOM_LENGTH);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            suffix.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return OrderNo.of("ORD-" + KoreaTime.toDate(now).format(DATE) + "-" + suffix);
    }
}
