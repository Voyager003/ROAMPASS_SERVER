package com.roampass_server.order.domain.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import com.roampass_server.common.domain.KoreaTime;
import com.roampass_server.order.domain.model.OrderNo;

/**
 * {@code ORD-{KST yyyyMMdd}-{8 random chars}}. The alphabet leaves out 0/O and 1/I so numbers read
 * aloud over support calls are not misheard. 32^8 ≈ 1.1 × 10^12 combinations per day; a collision is
 * still caught by the unique constraint on order_no at save time.
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
