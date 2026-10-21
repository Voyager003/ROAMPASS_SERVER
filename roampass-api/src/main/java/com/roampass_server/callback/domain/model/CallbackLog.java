package com.roampass_server.callback.domain.model;

import java.time.Instant;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 외부 통지(웹훅·콜백) 수신 기록. 추가 위주, 처리 결과만 갱신
 * eventKey 유니크로 중복 통지 판정. 서명이 틀린 통지는 eventKey 없이 기록
 * (위조 요청이 진짜 이벤트의 키를 선점하지 못하도록)
 */
public final class CallbackLog {

    private final Long id;
    private final CallbackSource source;
    private final String eventKey;
    private final OrderNo orderNo;
    private final boolean signatureOk;
    private final String payload;
    private final CallbackOutcome outcome;
    private final String note;
    private final Instant receivedAt;

    private CallbackLog(Long id, CallbackSource source, String eventKey, OrderNo orderNo, boolean signatureOk,
                        String payload, CallbackOutcome outcome, String note, Instant receivedAt) {
        this.id = id;
        this.source = source;
        this.eventKey = eventKey;
        this.orderNo = orderNo;
        this.signatureOk = signatureOk;
        this.payload = payload;
        this.outcome = outcome;
        this.note = note;
        this.receivedAt = receivedAt;
    }

    /** 서명 검증을 통과한 통지. 주문번호는 통지 내용으로 알 수 있으면 채움(어드민 주문 상세 조회용) */
    public static CallbackLog received(CallbackSource source, String eventKey, OrderNo orderNo, String payload,
                                       Instant now) {
        if (source == null || eventKey == null || eventKey.isBlank() || payload == null || now == null) {
            throw new InvalidValueException("INVALID_CALLBACK_LOG", "Source, event key, payload and time are required");
        }
        return new CallbackLog(null, source, eventKey, orderNo, true, payload, CallbackOutcome.RECEIVED, null, now);
    }

    /** 서명 불일치 통지, eventKey 없이 기록 */
    public static CallbackLog signatureRejected(CallbackSource source, String payload, Instant now) {
        if (source == null || payload == null || now == null) {
            throw new InvalidValueException("INVALID_CALLBACK_LOG", "Source, payload and time are required");
        }
        return new CallbackLog(null, source, null, null, false, payload, CallbackOutcome.SIGNATURE_INVALID, null,
                now);
    }

    /** 저장소에서 읽은 값으로 복원, 검증 없음 */
    public static CallbackLog restore(Long id, CallbackSource source, String eventKey, OrderNo orderNo,
                                      boolean signatureOk, String payload, CallbackOutcome outcome, String note,
                                      Instant receivedAt) {
        return new CallbackLog(id, source, eventKey, orderNo, signatureOk, payload, outcome, note, receivedAt);
    }

    public Long id() {
        return id;
    }

    public CallbackSource source() {
        return source;
    }

    public String eventKey() {
        return eventKey;
    }

    public OrderNo orderNo() {
        return orderNo;
    }

    public boolean signatureOk() {
        return signatureOk;
    }

    public String payload() {
        return payload;
    }

    public CallbackOutcome outcome() {
        return outcome;
    }

    public String note() {
        return note;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
